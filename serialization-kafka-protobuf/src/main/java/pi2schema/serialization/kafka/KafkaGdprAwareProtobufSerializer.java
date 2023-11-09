package pi2schema.serialization.kafka;

import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.Encryptor;
import pi2schema.crypto.LocalEncryptor;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

public class KafkaGdprAwareProtobufSerializer<T extends Message> implements Serializer<T> {

    private static final RecordHeaders EMPTY_HEADERS = new RecordHeaders();

    private final KafkaProtobufSerializer<T> inner;
    private ProtobufEncryptionEngine<T> protobufEncryptionEngine;
    private EncryptingMaterialsProvider materialsProvider;

    public KafkaGdprAwareProtobufSerializer() {
        this.inner = new KafkaProtobufSerializer<>();
        this.protobufEncryptionEngine = new UnconfiguredSerializer();
    }

    KafkaGdprAwareProtobufSerializer(
        Encryptor encryptor,
        SchemaRegistryClient schemaRegistry,
        Map<String, ?> configs,
        Class<T> clazz
    ) {
        this.protobufEncryptionEngine = new ProtobufEncryptionEngine<>(encryptor);
        this.inner = new KafkaProtobufSerializer<>(schemaRegistry, configs);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        inner.configure(configs, isKey);
        materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        protobufEncryptionEngine = new ProtobufEncryptionEngine<>(new LocalEncryptor(materialsProvider));
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return this.serialize(topic, EMPTY_HEADERS, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (data == null) {
            return null;
        }

        T encrypted = protobufEncryptionEngine.encrypt(data);

        return inner.serialize(topic, headers, encrypted);
    }

    @Override
    public void close() {
        materialsProvider.close();
        inner.close();
    }

    private class UnconfiguredSerializer extends ProtobufEncryptionEngine<T> {

        public UnconfiguredSerializer() {
            super(null);
        }

        @Override
        public T encrypt(@NotNull T data) {
            throw new UnconfiguredException();
        }
    }
}
