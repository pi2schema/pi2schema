package pi2schema.serialization.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import pi2schema.crypto.LocalEncryptor;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

public final class KafkaGdprAwareAvroProducerInterceptor<K, V extends SpecificRecordBase>
    implements ProducerInterceptor<K, V> {

    private AvroEncryptionEngine<V> encryptionEngine;
    private EncryptingMaterialsProvider materialsProvider;

    public KafkaGdprAwareAvroProducerInterceptor() {}

    public KafkaGdprAwareAvroProducerInterceptor(
        AvroEncryptionEngine<V> encryptionEngine,
        EncryptingMaterialsProvider materialsProvider
    ) {
        this.encryptionEngine = encryptionEngine;
        this.materialsProvider = materialsProvider;
    }

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record == null || record.value() == null) return record;

        return new ProducerRecord<>(
            record.topic(),
            record.partition(),
            record.timestamp(),
            record.key(),
            encryptionEngine.encrypt(record.value())
        );
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}

    @Override
    public void close() {
        materialsProvider.close();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        encryptionEngine = new AvroEncryptionEngine<>(new LocalEncryptor(materialsProvider));
    }
}
