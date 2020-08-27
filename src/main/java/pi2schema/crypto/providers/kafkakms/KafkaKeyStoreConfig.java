package pi2schema.crypto.providers.kafkakms;

import com.google.protobuf.Message;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import piischema.kms.KafkaProvider.Commands;
import piischema.kms.KafkaProvider.Subject;
import piischema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import java.util.HashMap;
import java.util.Map;

public class KafkaKeyStoreConfig extends AbstractConfig {

    private static final ConfigDef CONFIG;

    public static final String KMS_APPLICATION_ID_CONFIG = "piischema.kms.kafka.application.id";
    public static final String KMS_APPLICATION_ID_DEFAULT = "piischema.kms";
    public static final String KMS_APPLICATION_ID_DOC = "The kafka streams application id used by the kafka store";

    public static final String TOPIC_COMMANDS_CONFIG = "piischema.kms.kafka.commands.topic";
    public static final String TOPIC_COMMANDS_DEFAULT = "piischema.kms.commands";
    public static final String TOPIC_COMMANDS_DOC = "Topic used to store the commands";

    private final SerdeFactory serdeFactory = new SerdeFactory();

    private final Topics topics;
    private final Stores stores;

    static {
        CONFIG = new ConfigDef()
                .define(KMS_APPLICATION_ID_CONFIG,
                        ConfigDef.Type.STRING,
                        KMS_APPLICATION_ID_DEFAULT,
                        ConfigDef.Importance.LOW,
                        KMS_APPLICATION_ID_DOC
                )
                .define(TOPIC_COMMANDS_CONFIG,
                        ConfigDef.Type.STRING,
                        TOPIC_COMMANDS_DEFAULT,
                        ConfigDef.Importance.LOW,
                        TOPIC_COMMANDS_DOC
                );
    }

    KafkaKeyStoreConfig(Map<?, ?> provided) {
        super(CONFIG, provided, true);

        this.topics = new Topics();
        this.stores = new Stores();
    }

    Map<String, Object> toKafkaStreamsConfig() {
        Map<String, Object> values = new HashMap<>(this.values());

        values.put(StreamsConfig.APPLICATION_ID_CONFIG, this.get(KMS_APPLICATION_ID_CONFIG));
        values.remove(KMS_APPLICATION_ID_CONFIG);

        return values;
    }

    Topics topics() {
        return topics;
    }

    Stores stores() {
        return stores;
    }


    class Topics {
        final Topic<Subject, Commands> COMMANDS;

        private Topics() {
            COMMANDS = new Topic<>(
                    getString(KafkaKeyStoreConfig.TOPIC_COMMANDS_CONFIG),
                    serdeFactory.serdeFor(Subject.class, true),
                    serdeFactory.serdeFor(Commands.class, false)
            );
        }
    }

    class Stores {
        final Store<Subject, SubjectCryptographicMaterialAggregate> LOCAL_STORE;
        final Store<Subject, SubjectCryptographicMaterialAggregate> GLOBAL_AGGREGATE;

        private Stores() {
            LOCAL_STORE = new Store<>("local",
                    serdeFactory.serdeFor(Subject.class, true),
                    serdeFactory.serdeFor(SubjectCryptographicMaterialAggregate.class, false)
            );

            GLOBAL_AGGREGATE = new Store<>("global",
                    serdeFactory.serdeFor(Subject.class, true),
                    serdeFactory.serdeFor(SubjectCryptographicMaterialAggregate.class, false)
            );
        }
    }

    /**
     * Simple factory intended to hand always ready and configured serdes.
     */
    private class SerdeFactory {

        <T extends Message> Serde<T> serdeFor(Class<T> type, boolean isKey) {
            KafkaProtobufSerde<T> serde = new KafkaProtobufSerde<>(type);
            serde.configure(originals(), isKey);
            return serde;
        }

    }

    static class Topic<K, V> extends AbstractKafkaPersistence<K, V> {

        Topic(String name, Serde<K> keySerde, Serde<V> valueSerde) {
            super(name, keySerde, valueSerde);
        }

    }

    static class Store<K, V> extends AbstractKafkaPersistence<K, V> {

        Store(final String name, final Serde<K> keySerde, final Serde<V> valueSerde) {
            super(name, keySerde, valueSerde);
        }

        Materialized<K, V, KeyValueStore<Bytes, byte[]>> materialization() {
            return Materialized.<K, V>as(org.apache.kafka.streams.state.Stores.persistentKeyValueStore(name))
                    .withKeySerde(keySerde)
                    .withValueSerde(valueSerde);
        }

        StoreBuilder<KeyValueStore<K, V>> storeSupplier() {
            return org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                    org.apache.kafka.streams.state.Stores.persistentKeyValueStore(name),
                    keySerde,
                    valueSerde
            );
        }
    }

    static abstract class AbstractKafkaPersistence<K, V> {

        protected final String name;
        protected final Serde<K> keySerde;
        protected final Serde<V> valueSerde;

        AbstractKafkaPersistence(final String name, final Serde<K> keySerde, final Serde<V> valueSerde) {
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        Serializer<K> keySerializer() {
            return keySerde.serializer();
        }

        Serializer<V> valueSerializer() {
            return valueSerde.serializer();
        }

        Consumed<K, V> consumed() {
            return Consumed.with(
                    keySerde,
                    valueSerde
            );
        }

        String name() {
            return name;
        }

        public String toString() {
            return name;
        }
    }

}
