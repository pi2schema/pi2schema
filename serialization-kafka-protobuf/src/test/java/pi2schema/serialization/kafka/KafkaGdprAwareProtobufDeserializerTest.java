package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.Decryptor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static pi2schema.EncryptedPersonalDataV1.EncryptedPersonalData;

public class KafkaGdprAwareProtobufDeserializerTest {

    private final String topic = "test";
    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    private final Map<String, Object> configs;
    private final KafkaProtobufSerializer serializer;

    private final Decryptor noOpDecryptor = (subj, encryptedData) ->
            CompletableFuture.completedFuture(encryptedData.data());

    public KafkaGdprAwareProtobufDeserializerTest() {
        this.configs = Map.of(
                KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true,
                KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
        this.serializer = new KafkaProtobufSerializer(schemaRegistry, configs);
    }

    @Test
    public void shouldSupportNullRecordReturningNullData() {
        var deserializer = new KafkaGdprAwareProtobufDeserializer<>(
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
        var preferredMelon = FruitFixture.waterMelon().build();

        var deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                noOpDecryptor,
                schemaRegistry,
                configs,
                Fruit.class
        );

        var serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }

    @Test
    public void shouldDecryptEncryptedPersonalData() {
        var uuid = UUID.randomUUID().toString();
        var encrypted = ByteString.copyFrom("encryptedMocked".getBytes());
        var decrypted = FarmerRegisteredEventFixture.johnDoe().getContactInfo().toByteString();

        var encryptedEvent = FarmerRegisteredEvent.newBuilder()
                .setUuid(uuid)
                .setEncryptedPersonalData(EncryptedPersonalData.newBuilder()
                        .setSubjectId(uuid)
                        .setData(encrypted)
                        .setPersonalDataFieldNumber(2))
                .setRegisteredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();

        Decryptor decryptor = (subj, data) ->
                CompletableFuture.completedFuture(decrypted.asReadOnlyByteBuffer());

        var deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                decryptor,
                schemaRegistry,
                configs,
                FarmerRegisteredEvent.class
        );

        //when
        var serialized = serializer.serialize(topic, encryptedEvent);
        var actual = deserializer.deserialize(topic, serialized);

        //then
        assertThat(actual.getUuid())
                .isEqualTo(uuid);

        assertThat(actual.getPersonalDataCase())
                .isEqualTo(FarmerRegisteredEvent.PersonalDataCase.CONTACT_INFO);

        assertThat(actual.getContactInfo())
                .isEqualTo(FarmerRegisteredEventFixture.johnDoe().getContactInfo());
    }
}
