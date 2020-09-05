package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import com.acme.TimestampFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.IvParameterSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KafkaGdprAwareProtobufSerializerTest {

    private final Map<String, Object> configs;

    private final String topic = "test";
    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();

    private final byte[] encrypted = "mockEncryption".getBytes();
    private final Encryptor encryptorMock = (subjectId, data) ->
            CompletableFuture.completedFuture(
                    new EncryptedData(encrypted, "AES/CBC/PKCS5Padding", new IvParameterSpec(new byte[0]))
            );

    public KafkaGdprAwareProtobufSerializerTest() {
        HashMap<String, Object> initial = new HashMap<>();
        initial.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        initial.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
        this.configs = Collections.unmodifiableMap(initial);
    }

    @Test
    public void shouldSupportNullRecordReturningNullData() {
        KafkaGdprAwareProtobufSerializer<Fruit> deserializer = new KafkaGdprAwareProtobufSerializer<>(
                encryptorMock,
                schemaRegistry,
                configs, Fruit.class);

        assertNull(deserializer.serialize(topic, null));
        assertNull(deserializer.serialize(topic, new RecordHeaders(), null));
    }

    @Test
    public void shouldBeCompatibleWithPlainProtobufDeserializer() {

        Fruit preferredMelon = FruitFixture.waterMelon().build();

        KafkaGdprAwareProtobufSerializer<Fruit> serializer = new KafkaGdprAwareProtobufSerializer<>(
                encryptorMock,
                schemaRegistry,
                configs, Fruit.class);
        KafkaProtobufDeserializer<Fruit> deserializer = new KafkaProtobufDeserializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }

    @Test
    public void shouldEncryptOneOfFieldsContainingEncryptedPersonalData() {
        //given
        String uuid = UUID.randomUUID().toString();

        FarmerRegisteredEvent original = FarmerRegisteredEventFixture.johnDoe()
                .setUuid(uuid)
                .setRegisteredAt(TimestampFixture.now())
                .build();

        KafkaGdprAwareProtobufSerializer<FarmerRegisteredEvent> serializer = new KafkaGdprAwareProtobufSerializer<>(
                encryptorMock,
                schemaRegistry,
                configs,
                FarmerRegisteredEvent.class);
        KafkaProtobufDeserializer<FarmerRegisteredEvent> deserializer = new KafkaProtobufDeserializer<>(
                schemaRegistry,
                configs,
                FarmerRegisteredEvent.class
        );

        //when
        byte[] data = serializer.serialize(topic, original);
        FarmerRegisteredEvent actual = deserializer.deserialize(topic, data);

        //then
        assertThat(actual.getUuid())
                .isEqualTo(uuid);

        assertThat(actual.getPersonalDataCase())
                .isEqualTo(FarmerRegisteredEvent.PersonalDataCase.ENCRYPTEDPERSONALDATA);

        assertThat(actual.getEncryptedPersonalData().getSubjectId())
                .isEqualTo(uuid);

        assertThat(actual.getEncryptedPersonalData().getData().toByteArray())
                .isEqualTo(encrypted);

        assertThat(actual.getEncryptedPersonalData().getPersonalDataFieldNumber())
                .isEqualTo(2);

    }
}