package pi2schema.crypto.providers.kafkakms;

import com.google.protobuf.ByteString;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import piischema.kms.KafkaProvider.*;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

public class KafkaKeyStore {

    private static final Logger log = LoggerFactory.getLogger(KafkaKeyStore.class);

    private final KafkaKeyStoreConfig config;
    private final KafkaStreams streams;
    private final ReadOnlyKeyValueStore<Subject, SubjectCryptographicMaterialAggregate> store;

    private final KafkaProducer<Subject, Commands> producer;
    private final Map<Subject, CompletableFuture<SubjectCryptographicMaterialAggregate>> waitingCreation = new HashMap<>();

    public KafkaKeyStore(Map<String, Object> configs) {
        this.config = new KafkaKeyStoreConfig(configs);
        //todo stop me
        this.producer = new KafkaProducer<>(configs,
                config.topics().COMMANDS.keySerializer(),
                config.topics().COMMANDS.valueSerializer()
        );
        this.streams = startStreams(configs);
        this.store = this.streams.store(
                StoreQueryParameters.fromNameAndType(
                        config.stores().GLOBAL_AGGREGATE.name(),
                        QueryableStoreTypes.keyValueStore())
        );
    }

    public SubjectCryptographicMaterialAggregate cryptoMaterialsFor(String subjectId) {


        Subject subject = Subject.newBuilder().setId(subjectId).build();
        SubjectCryptographicMaterialAggregate existent = store.get(subject);

        if (existent != null) {
            return existent;
        }

        Commands command = Commands.newBuilder()
                .setCreate(CreateCryptographicMaterials.newBuilder()
                        .setSubject(subject)
                        .build())
                .build();
        producer.send(new ProducerRecord<>(
                config.getString(KafkaKeyStoreConfig.TOPIC_COMMANDS_CONFIG),
                subject,
                command));

        CompletableFuture<SubjectCryptographicMaterialAggregate> completable = new CompletableFuture<>();
        this.waitingCreation.put(subject, completable);

        try {
            return completable.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e); // type me
        }
    }

    private KafkaStreams startStreams(Map<String, ?> providedConfigs) {
        Properties properties = new Properties();
        properties.putAll(providedConfigs);
        properties.putAll(config.toKafkaStreamsConfig());

        Topology topology = createKafkaKeyStoreTopology();

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

    Topology createKafkaKeyStoreTopology() {

        StreamsBuilder builder = new StreamsBuilder();

        KeyValueBytesStoreSupplier localCryptoMaterials = Stores.persistentKeyValueStore(config.stores().LOCAL_STORE.name());
        StoreBuilder<KeyValueStore<Subject, SubjectCryptographicMaterialAggregate>> allCryptoMaterials =
                Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore(config.stores().GLOBAL_AGGREGATE.name()),
                        config.stores().LOCAL_STORE.keySerde(),
                        config.stores().LOCAL_STORE.valueSerde()
                );

        KTable<Subject, SubjectCryptographicMaterialAggregate> shardedSubjects = builder
                .stream(
                        config.topics().COMMANDS.name(),
                        config.topics().COMMANDS.consumed()
                )
                .groupByKey()
                .aggregate(
                        SubjectCryptographicMaterialAggregate::getDefaultInstance,
                        new KmsCommandHandler(),
                        Materialized.<Subject, SubjectCryptographicMaterialAggregate>as(localCryptoMaterials)
                                .withKeySerde(config.stores().LOCAL_STORE.keySerde())
                                .withValueSerde(config.stores().LOCAL_STORE.valueSerde()));

        //TODO redo this part. This broadcast changelog topic should not exist
        shardedSubjects.toStream().to("broadcast");
        builder.addGlobalStore(
                allCryptoMaterials,
                "broadcast",
                Consumed.with(config.stores().GLOBAL_AGGREGATE.keySerde(), config.stores().GLOBAL_AGGREGATE.valueSerde()),
                () -> new AbstractProcessor<Subject, SubjectCryptographicMaterialAggregate>() {
                    @Override
                    public void process(Subject key, SubjectCryptographicMaterialAggregate value) {
                        CompletableFuture<SubjectCryptographicMaterialAggregate> waiting = waitingCreation.remove(key);
                        waiting.complete(value);
                    }
                }
        );

        return builder.build();
    }

    private static class KmsCommandHandler implements Aggregator<Subject, Commands, SubjectCryptographicMaterialAggregate> {

        private final KeyGenerator keyGenerator;

        private KmsCommandHandler() {
            keyGenerator = new JceKeyGenerator();
        }

        @Override
        public SubjectCryptographicMaterialAggregate apply(final Subject key, final Commands command, final SubjectCryptographicMaterialAggregate current) {

            final SubjectCryptographicMaterialAggregate newState;
            switch (command.getCommandCase()) {
                case CREATE:
                    if (current.getMaterialsList().isEmpty()) {
                        newState = current.toBuilder().addMaterials(
                                SubjectCryptographicMaterial.newBuilder()
                                        .setSubject(key)
                                        .setAlgorithm("AES")
                                        .setVersion(1)
                                        .setSymmetricKey(ByteString.copyFrom(keyGenerator.generate().getEncoded()))
                                        .build())
                                .build();
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

    private interface KeyGenerator {
        SecretKey generate();
    }

    private static class JceKeyGenerator implements KafkaKeyStore.KeyGenerator {

        private final javax.crypto.KeyGenerator jceGenerator;

        public JceKeyGenerator() {
            try {
                jceGenerator = javax.crypto.KeyGenerator.getInstance("AES");
                jceGenerator.init(256);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e); //todo type me
            }
        }

        @Override
        public SecretKey generate() {
            return jceGenerator.generateKey();
        }

    }
}
