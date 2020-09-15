package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.ContactInfo;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import pi2schema.crypto.materials.MissingCryptoMaterialsException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent.PersonalDataCase.ENCRYPTEDPERSONALDATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
class KafkaGdprAwareProtobufIntegrationTest {

    @Rule
    public KafkaContainer kafka = new KafkaContainer().withNetwork(Network.SHARED);

    private GenericContainer schemaRegistry;

    private final Map<String, Object> configs;

    private Fruit waterMelon = FruitFixture.waterMelon().build();

    KafkaGdprAwareProtobufIntegrationTest() {

        kafka.start();

        schemaRegistry = new GenericContainer("confluentinc/cp-schema-registry:5.5.1")
                .withNetwork(Network.SHARED)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + kafka.getNetworkAliases().get(0) + ":9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withExposedPorts(8081);

        schemaRegistry.start();

        String schemaRegistryUrl = "http://" + schemaRegistry.getContainerIpAddress() +
                ":" + schemaRegistry.getMappedPort(8081);

        HashMap<String, Object> configuring = new HashMap<>();
        configuring.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configuring.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        configuring.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        configuring.put(KafkaProtobufDeserializerConfig.DERIVE_TYPE_CONFIG, true);

        this.configs = Collections.unmodifiableMap(configuring);

        createTopics(configs, "pi2schema.kms.commands");
    }

    @Test
    void serializeShouldFailWithIllegalStateExceptionCaseTheConfigureMethodIsNotCalled() {

        //Default constructor is required by kafka for scenarios of configuration like key.serializer which the kafka
        // client uses reflection to instantiate and Configurable to initiate.
        KafkaGdprAwareProtobufSerializer<Fruit> serializer = new KafkaGdprAwareProtobufSerializer<>();
        UnconfiguredException unconfiguredException = assertThrows(UnconfiguredException.class, () ->
                serializer.serialize("", waterMelon));

        assertThat(unconfiguredException).hasMessageContaining("configure method");
    }

    @Test
    void deserializeShouldFailWithIllegalStateExceptionCaseTheConfigureMethodIsNotCalled() {

        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>();
        UnconfiguredException unconfiguredException = assertThrows(UnconfiguredException.class, () ->
                deserializer.deserialize("", new byte[0]));

        assertThat(unconfiguredException).hasMessageContaining("configure method");
    }

    @Test
    void configuredUsingKafkaMaterialsProvider() {
        FarmerRegisteredEvent eventWithPersonalData = FarmerRegisteredEventFixture.johnDoe().build();

        // serialization with personal data to be encrypted
        KafkaGdprAwareProtobufSerializer<FarmerRegisteredEvent> serializer = new KafkaGdprAwareProtobufSerializer<>();
        serializer.configure(configs, false);
        byte[] serializedWithCryptoData = serializer.serialize("", eventWithPersonalData);


        // standard deserialization, should be compatible and provide a encrypted value
        try (KafkaProtobufDeserializer<FarmerRegisteredEvent> standardDeserializer = new KafkaProtobufDeserializer<>()) {
            standardDeserializer.configure(configs, false);
            FarmerRegisteredEvent deserializedEncrypted = standardDeserializer.deserialize("", serializedWithCryptoData);

            assertThat(deserializedEncrypted).isNotNull();
            assertThat(deserializedEncrypted.getPersonalDataCase()).isEqualByComparingTo(ENCRYPTEDPERSONALDATA);
            assertThat(deserializedEncrypted.getContactInfo()).isEqualTo(ContactInfo.getDefaultInstance());
            assertThat(eventWithPersonalData).isNotEqualTo(deserializedEncrypted);
        }

        try (KafkaGdprAwareProtobufDeserializer<FarmerRegisteredEvent> deserializer = new KafkaGdprAwareProtobufDeserializer<>()) {
            deserializer.configure(configs, false);

            await().atMost(Duration.ofSeconds(600)).untilAsserted(
                    () -> {
                        try {
                            FarmerRegisteredEvent decrypted = deserializer.deserialize("", serializedWithCryptoData);
                            assertThat(eventWithPersonalData).isEqualTo(decrypted);
                        } catch (CompletionException e) {
                            // failed exception in order to be retried by the awaitability.
                            if (e.getCause() instanceof MissingCryptoMaterialsException) {
                                fail(e);
                            }
                            throw e;
                        }
                    }
            );
        } finally {
            //also close the initial serializer
            serializer.close();
        }

    }

    private void createTopics(Map<String, Object> configs, String... topics) {
        List<NewTopic> newTopics =
                Arrays.stream(topics)
                        .map(topic -> new NewTopic(topic, 1, (short) 1))
                        .collect(Collectors.toList());

        try (AdminClient admin = AdminClient.create(configs)) {
            admin.createTopics(newTopics);
        }
    }

}