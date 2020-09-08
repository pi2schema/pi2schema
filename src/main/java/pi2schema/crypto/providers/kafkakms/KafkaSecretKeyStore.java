package pi2schema.crypto.providers.kafkakms;

import com.google.protobuf.ByteString;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.kms.KafkaProvider;
import pi2schema.kms.KafkaProvider.*;

import javax.crypto.KeyGenerator;
import java.io.Closeable;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Kafka backed keystore, for development purposes without external dependencies (ie: vault or aws/gcp kms).
 * <p>
 * TODO:
 * - publish events
 * - right to be forgotten
 * - avoid global store / partitioning aware?
 */
public class KafkaSecretKeyStore implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(KafkaSecretKeyStore.class);

    private final KeyGenerator keyGenerator;

    private final KafkaSecretKeyStoreConfig config;
    private final KafkaStreams streams;
    private final ReadOnlyKeyValueStore<Subject, SubjectCryptographicMaterialAggregate> allKeys;

    private final KafkaProducer<Subject, Commands> commandProducer;

    public KafkaSecretKeyStore(KeyGenerator keyGenerator, Map<String, ?> configs) {
        this.keyGenerator = keyGenerator;
        this.config = new KafkaSecretKeyStoreConfig(configs);
        Properties producerConfig = new Properties();
        producerConfig.putAll(configs);
        this.commandProducer = new KafkaProducer<>(producerConfig,
                config.topics().COMMANDS.keySerializer(),
                config.topics().COMMANDS.valueSerializer()
        );
        this.streams = startStreams(configs);
        this.allKeys = this.streams.store(
                StoreQueryParameters.fromNameAndType(
                        config.stores().GLOBAL_AGGREGATE.name(),
                        QueryableStoreTypes.keyValueStore())
        );
    }

    CompletableFuture<SubjectCryptographicMaterialAggregate> retrieveOrCreateCryptoMaterialsFor(
            @NotNull String subjectId) {
        Subject subject = Subject.newBuilder().setId(subjectId).build();

        return existentMaterialsFor(subject)
                .thenCompose(material -> {
                    if (material != null) {
                        return CompletableFuture.completedFuture(material);
                    } else {
                        return createMaterialsFor(subject);
                    }
                });
    }

    private CompletableFuture<SubjectCryptographicMaterialAggregate> createMaterialsFor(Subject subject) {

        SubjectCryptographicMaterial cryptoMaterial = SubjectCryptographicMaterial.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSubject(subject)
                .setAlgorithm(keyGenerator.getAlgorithm())
                .setSymmetricKey(ByteString.copyFrom(keyGenerator.generateKey().getEncoded()))
                .build();

        Commands command = Commands.newBuilder()
                .setRegister(KafkaProvider.RegisterCryptographicMaterials.newBuilder().setMaterial(cryptoMaterial).build())
                .build();

        CompletableFuture<SubjectCryptographicMaterialAggregate> future = new CompletableFuture<>();
        return CompletableFuture.supplyAsync(() ->
                commandProducer.send(
                        new ProducerRecord<>(
                                config.getString(KafkaSecretKeyStoreConfig.TOPIC_COMMANDS_CONFIG),
                                subject,
                                command),
                        (metadata, exception) -> {
                            if (exception != null) {
                                future.completeExceptionally(exception);
                            } else {
                                future.complete(SubjectCryptographicMaterialAggregate
                                        .newBuilder().addMaterials(cryptoMaterial).build());
                            }
                        })
        ).thenComposeAsync(__ -> future);
    }

    CompletableFuture<SubjectCryptographicMaterialAggregate> existentMaterialsFor(String subjectId) {
        return existentMaterialsFor(Subject.newBuilder().setId(subjectId).build());
    }

    private CompletableFuture<SubjectCryptographicMaterialAggregate> existentMaterialsFor(
            Subject subject) {
        return CompletableFuture.supplyAsync(() -> allKeys.get(subject));
    }

    private KafkaStreams startStreams(Map<String, ?> providedConfigs) {
        Properties properties = new Properties();
        properties.putAll(providedConfigs);
        properties.putAll(config.toKafkaStreamsConfig());

        Topology topology = createKafkaKeyStoreTopology();

        log.debug("Created topology {}", topology.describe());
        log.debug("Starting topology with following configurations {}", properties);

        KafkaStreams streams = new KafkaStreams(topology, properties);

        final CountDownLatch startLatch = new CountDownLatch(1);
        streams.setStateListener((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING && oldState != KafkaStreams.State.RUNNING) {
                startLatch.countDown();
            }
        });

        streams.start();

        try {
            if (!startLatch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Streams never finished balancing on startup");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return streams;
    }

    private Topology createKafkaKeyStoreTopology() {

        StreamsBuilder builder = new StreamsBuilder();

        builder
                .stream(
                        config.topics().COMMANDS.name(),
                        config.topics().COMMANDS.consumed()
                )
                .groupByKey()
                .aggregate(
                        SubjectCryptographicMaterialAggregate::getDefaultInstance,
                        new KmsCommandHandler(),
                        config.stores().LOCAL_STORE.materialization());

        //TODO redo this part.
        builder.globalTable(config.stores().LOCAL_STORE.internalTopic(),
                config.stores().GLOBAL_AGGREGATE.consumed(),
                config.stores().GLOBAL_AGGREGATE.materialization());

        return builder.build();
    }

    @Override
    public void close() {
        log.debug("Stopping kafka key store [producer, streams].");
        this.commandProducer.close();
        this.streams.close();
        log.info("Kafka secret key store stopped");
    }

    private static class KmsCommandHandler implements Aggregator<Subject, Commands, SubjectCryptographicMaterialAggregate> {

        @Override
        public SubjectCryptographicMaterialAggregate apply(final Subject key, final Commands command, final SubjectCryptographicMaterialAggregate current) {

            final SubjectCryptographicMaterialAggregate newState;
            switch (command.getCommandCase()) {
                case REGISTER:
                    if (current.getMaterialsList().isEmpty()) {
                        newState = current.toBuilder().addMaterials(
                                command.getRegister().getMaterial()
                        ).build();
                    } else {
                        newState = current;
                        log.info("Secret key already present for subject {}, no key versioning implemented at the moment.", key);
                    }
                    break;

                case FORGET:
                    log.error("Forgotten feature not implemented yet.");
                    newState = current;
                    break;

                default:
                    log.error("Received unexpected command {}, supported commands at the moment are {}.",
                            command,
                            "[CREATE, FORGET]");
                    newState = current;
                    break;
            }

            return newState;
        }
    }
}
