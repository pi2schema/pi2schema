package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.github.gustavomonarin.kafkagdpr.core.encryption.Decryptor;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static piischema.EncryptedPersonalDataV1.EncryptedPersonalData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class KafkaGdprAwareProtobufDeserializerTest {

    private final String topic = "test";
    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    private final Map<String, Object> configs;
    private final KafkaProtobufSerializer serializer;

    private final Decryptor noOpDecryptor = (subj, encryptedData) -> encryptedData.data();

    public KafkaGdprAwareProtobufDeserializerTest() {
        HashMap<String, Object> initial = new HashMap<>();
        initial.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        initial.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
        this.configs = Collections.unmodifiableMap(initial);
        this.serializer = new KafkaProtobufSerializer(
                schemaRegistry,
                configs
        );
    }

    @Test
    public void shouldSupportNullRecordReturningNullData() {
        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                noOpDecryptor,
                schemaRegistry,
                configs,
                Fruit.class
        );

        assertNull(deserializer.deserialize(topic, null));
        assertNull(deserializer.deserialize(topic, new RecordHeaders(), null));
    }

    @Test
    public void shouldBeCompatibleWithPlainProtobufSerializer() {

        Fruit preferredMelon = FruitFixture.waterMelon().build();

        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                noOpDecryptor,
                schemaRegistry,
                configs,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }

    @Test
    public void shouldDecryptEncryptedPersonalData() {

        String uuid = UUID.randomUUID().toString();
        ByteString encrypted = ByteString.copyFrom("encryptedMocked".getBytes());
        ByteString decrypted = FarmerRegisteredEventFixture.johnDoe().getContactInfo().toByteString();

        FarmerRegisteredEvent encryptedEvent = FarmerRegisteredEvent.newBuilder()
                .setUuid(uuid)
                .setEncryptedPersonalData(EncryptedPersonalData.newBuilder()
                        .setSubjectId(uuid)
                        .setData(encrypted)
                        .setPersonalDataFieldNumber(2))
                .setRegisteredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();

        Decryptor decryptor = (subj, data) -> decrypted.toByteArray();

        KafkaGdprAwareProtobufDeserializer<FarmerRegisteredEvent> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                decryptor,
                schemaRegistry,
                configs,
                FarmerRegisteredEvent.class
        );

        //when
        byte[] serialized = serializer.serialize(topic, encryptedEvent);
        FarmerRegisteredEvent actual = deserializer.deserialize(topic, serialized);

        //then
        assertThat(actual.getUuid())
                .isEqualTo(uuid);

        assertThat(actual.getPersonalDataCase())
                .isEqualTo(FarmerRegisteredEvent.PersonalDataCase.CONTACT_INFO);

        assertThat(actual.getContactInfo())
                .isEqualTo(FarmerRegisteredEventFixture.johnDoe().getContactInfo());


    }


}
