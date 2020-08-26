package pi2schema.crypto.providers.kafkakms;

import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyDescription;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import pi2schema.crypto.materials.EncryptingMaterial;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
class MostRecentMaterialsProviderTest {

    @Rule
    public KafkaContainer kafka = new KafkaContainer();

    public GenericContainer schemaRegistry;
    private Map<String, Object> configs;

    @BeforeEach
    void setUp() {

        kafka.start();

        schemaRegistry = new GenericContainer("confluentinc/cp-schema-registry:5.5.1")
                .withNetwork(kafka.getNetwork())
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + kafka.getNetworkAliases().get(0) + ":9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withExposedPorts(8081);

        schemaRegistry.start();

        String schemaRegistryUrl = new StringBuilder()
                .append("http://").append(schemaRegistry.getContainerIpAddress())
                .append(":").append(schemaRegistry.getMappedPort(8081))
                .toString();

        configs = new HashMap<>();
        configs.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        createTopics(configs, "piischema.kms.commands", "broadcast");
    }

    @Test
    void topology() {

        Topology topology = new KafkaKeyStore(configs).createKafkaKeyStoreTopology();

        TopologyDescription describe = topology.describe();

        System.out.println(describe);

    }


    @Test
    void find() {

        MostRecentMaterialsProvider provider = new MostRecentMaterialsProvider(configs);

        EncryptingMaterial materialForSubject1 = provider.encryptionKeysFor("subject1");
        EncryptingMaterial materialForSubject1SecondCall = provider.encryptionKeysFor("subject1");

        assertThat(materialForSubject1.getEncryptionKey()).isNotNull();
        assertThat(materialForSubject1.getEncryptionKey().getAlgorithm())
                .isEqualTo("AES");
        assertThat(materialForSubject1.getEncryptionKey().getEncoded())
                .isNotEmpty();


        assertThat(materialForSubject1)
                .isEqualTo(materialForSubject1SecondCall);

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