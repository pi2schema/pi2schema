package pi2schema.serialization.kafka;

import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.jetbrains.annotations.Nullable;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.LocalDecryptor;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

public class KafkaGdprAwareProtobufDeserializer<T extends Message> implements Deserializer<T> {

    private static final RecordHeaders EMPTY_HEADERS = new RecordHeaders();

    private Deserializer<T> inner;
    private ProtobufDecryptionEngine<T> decryptionEngine;
    private DecryptingMaterialsProvider materialsProvider;

    public KafkaGdprAwareProtobufDeserializer() {
        this.inner = new UncofiguredDeserializer<>();
    }

    KafkaGdprAwareProtobufDeserializer(
        Decryptor decryptor,
        SchemaRegistryClient schemaRegistry,
        Map<String, ?> configs,
        Class<T> clazz
    ) {
        this.inner = new KafkaProtobufDeserializer<>(schemaRegistry, configs, clazz);
        decryptionEngine = new ProtobufDecryptionEngine<>(decryptor);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (this.decryptionEngine != null || !(this.inner instanceof UncofiguredDeserializer)) {
            throw new IllegalStateException(
                "Configure method was called even though the deserializer was already configured"
            );
        }

        this.inner = new KafkaProtobufDeserializer<>();
        this.inner.configure(configs, isKey);
        this.materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        this.decryptionEngine = new ProtobufDecryptionEngine<>(new LocalDecryptor(materialsProvider));
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        return this.deserialize(topic, EMPTY_HEADERS, data);
    }

    @Override
    public T deserialize(String topic, Headers headers, @Nullable byte[] data) {
        if (data == null) {
            return null;
        }

        T deserialized = this.inner.deserialize(topic, headers, data);

        return decryptionEngine.decrypt(deserialized);
    }

    @Override
    public void close() {
        this.inner.close();
        this.materialsProvider.close();
    }

    private class UncofiguredDeserializer<T> implements Deserializer<T> {

        @Override
        public T deserialize(String topic, byte[] data) {
            throw new UnconfiguredException();
        }
    }
}
