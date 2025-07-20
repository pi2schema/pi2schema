package pi2schema.crypto.providers.kafkakms;

import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import pi2schema.crypto.support.KeyGen;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pi2schema.crypto.providers.kafkakms.KafkaTestUtils.createTopics;

@Testcontainers
class KafkaSecretKeyStoreTest {

    @Container
    public RedpandaContainer redpandaContainer = new RedpandaContainer(
        "docker.redpanda.com/redpandadata/redpanda:v24.3.18"
    );

    private Map<String, Object> configs;
    private KafkaSecretKeyStore store;

    @BeforeEach
    void setUp() {
        configs = new HashMap<>();
        configs.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, redpandaContainer.getBootstrapServers());
        configs.put(
            KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
            redpandaContainer.getSchemaRegistryAddress()
        );

        createTopics(configs, "pi2schema.kms.commands");

        store = new KafkaSecretKeyStore(KeyGen.aes256(), configs);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void getOrCreate() {
        await().atMost(Duration.ofSeconds(20)).until(() -> store.isRunning());

        var subject = UUID.randomUUID().toString();

        // create
        var firstMaterials = store.retrieveOrCreateCryptoMaterialsFor(subject).join();

        assertThat(firstMaterials.getMaterialsList()).hasSize(1);
        assertThat(firstMaterials.getMaterials(0).getAlgorithm()).isEqualTo("AES");
        assertThat(firstMaterials.getMaterials(0).getSymmetricKey()).isNotEmpty();

        // retrieve
        // the key propagation is asynchronous
        await()
            .atMost(Duration.ofSeconds(120))
            .untilAsserted(() -> {
                SubjectCryptographicMaterialAggregate retrievedMaterials = store.existentMaterialsFor(subject).join();
                assertThat(retrievedMaterials).isEqualTo(firstMaterials);
            });

        // once the key is propagated, should reuse the previous key and not create new ones
        var retrieveOrCreateSecond = store.retrieveOrCreateCryptoMaterialsFor(subject).join();

        assertThat(firstMaterials).isEqualTo(retrieveOrCreateSecond);
    }
}
