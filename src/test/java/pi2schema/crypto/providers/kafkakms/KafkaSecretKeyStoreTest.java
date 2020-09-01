package pi2schema.crypto.providers.kafkakms;

import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
class KafkaSecretKeyStoreTest {

    @Rule
    public KafkaContainer kafka = new KafkaContainer().withNetwork(Network.SHARED);

    public GenericContainer schemaRegistry;
    private Map<String, Object> configs;
    private KafkaSecretKeyStore store;

    @BeforeEach
    void setUp() {

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

        configs = new HashMap<>();
        configs.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        createTopics(configs, "pi2schema.kms.commands");

        store = new KafkaSecretKeyStore(configs);
    }

    @AfterEach
    void tearDown() {
        store.close();
        schemaRegistry.close();
        kafka.close();
    }

    @Test
    void getOrCreate() {

        //create
        SubjectCryptographicMaterialAggregate materialForSubject1 = store.getOrCreateCryptoMaterialsFor("subject1");
        //get
        SubjectCryptographicMaterialAggregate materialForSubject1SecondCall = store.getOrCreateCryptoMaterialsFor("subject1");

        assertThat(materialForSubject1.getMaterialsList()).hasSize(1);
        assertThat(materialForSubject1.getMaterials(0).getAlgorithm())
                .isEqualTo("AES");
        assertThat(materialForSubject1.getMaterials(0).getSymmetricKey())
                .isNotEmpty();

        assertThat(materialForSubject1)
                .isEqualTo(materialForSubject1SecondCall);

    }

    @Test
    void existentMaterials(){

        String subject = UUID.randomUUID().toString();

        SubjectCryptographicMaterialAggregate createdMaterials = store.getOrCreateCryptoMaterialsFor(subject);

        SubjectCryptographicMaterialAggregate retrievedMaterials = store.existentMaterialsFor(subject);

        assertThat(createdMaterials.getMaterialsList()).hasSize(1);
        assertThat(createdMaterials)
                .isEqualTo(retrievedMaterials);
    }


    private static void createTopics(Map<String, Object> configs, String... topics) {
        List<NewTopic> newTopics =
                Arrays.stream(topics)
                        .map(topic -> new NewTopic(topic, 1, (short) 1))
                        .collect(Collectors.toList());

        try (AdminClient admin = AdminClient.create(configs)) {
            admin.createTopics(newTopics);
        }
    }

}