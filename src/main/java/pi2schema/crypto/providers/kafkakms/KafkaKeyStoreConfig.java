package pi2schema.crypto.providers.kafkakms;

import com.google.protobuf.Message;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
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

    public Topics topics() {
        return topics;
    }

    public Stores stores() {
        return stores;
    }


    class Topics {
        public final Topic<Subject, Commands> COMMANDS;

        private Topics() {
            COMMANDS = new Topic<>(
                    getString(KafkaKeyStoreConfig.TOPIC_COMMANDS_CONFIG),
                    serdeFactory.serdeFor(Subject.class, true),
                    serdeFactory.serdeFor(Commands.class, false)
            );
        }
    }

    class Stores {
        public final Store<Subject, SubjectCryptographicMaterialAggregate> LOCAL_STORE;
        public final Store<Subject, SubjectCryptographicMaterialAggregate> GLOBAL_AGGREGATE;

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

    public static class Topic<K, V> {

        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;

        Topic(final String name, final Serde<K> keySerde, final Serde<V> valueSerde) {
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        public Serializer<K> keySerializer() {
            return keySerde.serializer();
        }

        public Serializer<V> valueSerializer() {
            return valueSerde.serializer();
        }

        public String name() {
            return name;
        }

        public String toString() {
            return name;
        }

        public Consumed<K, V> consumed() {
            return Consumed.with(
                    keySerde,
                    valueSerde
            );
        }
    }

    public static class Store<K, V> {

        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;

        Store(final String name, final Serde<K> keySerde, final Serde<V> valueSerde) {
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        public Serde<K> keySerde() {
            return keySerde;
        }

        public Serde<V> valueSerde() {
            return valueSerde;
        }

        public String name() {
            return name;
        }

        public String toString() {
            return name;
        }
    }


}
