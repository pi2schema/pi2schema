package pi2schema.serialization.kafka;

import com.google.protobuf.Message;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import pi2schema.crypto.LocalEncryptor;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

public final class KafkaGdprAwareProtobufProducerInterceptor<K, V extends Message> implements ProducerInterceptor<K, V> {
    private ProtobufEncryptionEngine<V> encryptionEngine;
    private EncryptingMaterialsProvider materialsProvider;

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        return new ProducerRecord<>(
                record.topic(),
                record.partition(),
                record.timestamp(),
                record.key(),
                encryptionEngine.encrypt(record.value())
        );
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
        materialsProvider.close();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        encryptionEngine = new ProtobufEncryptionEngine<>(new LocalEncryptor(materialsProvider));
    }
}
