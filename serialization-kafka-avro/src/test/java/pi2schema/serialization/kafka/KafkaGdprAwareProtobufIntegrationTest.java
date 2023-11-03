package pi2schema.serialization.kafka;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Testcontainers
class KafkaGdprAwareProtobufIntegrationTest {

    @Container
    public RedpandaContainer redpandaContainer = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v23.2.14");

    private Map<String, Object> configs;


    @BeforeEach
    void beforeEach() {

        var configuring = new HashMap<String, Object>();
        configuring.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, redpandaContainer.getBootstrapServers());
        configuring.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, redpandaContainer.getSchemaRegistryAddress());
        configuring.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
//        configuring.put(AbstractKafkaSchemaSerDeConfig.DERIVE_TYPE_CONFIG, true);

        this.configs = Collections.unmodifiableMap(configuring);

        createTopics(configs, "pi2schema.kms.commands");
    }


    @Test
    void configuredUsingKafkaMaterialsProvider() {


    }

    private void createTopics(Map<String, Object> configs, String... topics) {
        var newTopics =
                Arrays.stream(topics)
                        .map(topic -> new NewTopic(topic, 1, (short) 1))
                        .collect(Collectors.toList());

        try (var admin = AdminClient.create(configs)) {
            admin.createTopics(newTopics);
        }
    }
}