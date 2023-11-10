package pi2schema.serialization.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import pi2schema.crypto.LocalDecryptor;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;
import java.util.stream.Collectors;

public final class KafkaGdprAwareAvroConsumerInterceptor<K, V extends SpecificRecordBase>
    implements ConsumerInterceptor<K, V> {

    private AvroDecryptionEngine<V> decryptionEngine;
    private DecryptingMaterialsProvider materialsProvider;

    public KafkaGdprAwareAvroConsumerInterceptor() {}

    public KafkaGdprAwareAvroConsumerInterceptor(
        AvroDecryptionEngine<V> decryptionEngine,
        DecryptingMaterialsProvider materialsProvider
    ) {
        this.decryptionEngine = decryptionEngine;
        this.materialsProvider = materialsProvider;
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        var decryptedRecords = records
            .partitions()
            .stream()
            .map(partition -> {
                var decrypted = records
                    .records(partition)
                    .stream()
                    .map(record ->
                        new ConsumerRecord<>(
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.timestamp(),
                            record.timestampType(),
                            record.serializedKeySize(),
                            record.serializedValueSize(),
                            record.key(),
                            decryptionEngine.decrypt(record.value()),
                            record.headers(),
                            record.leaderEpoch()
                        )
                    )
                    .collect(Collectors.toList());
                return Map.of(partition, decrypted);
            })
            .reduce((m1, m2) -> {
                m1.putAll(m2);
                return m1;
            })
            .orElse(Map.of());
        return new ConsumerRecords<>(decryptedRecords);
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

    @Override
    public void close() {
        materialsProvider.close();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        if (this.decryptionEngine != null) {
            throw new IllegalStateException(
                "Configure method was called even though the deserializer was already configured"
            );
        }

        materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        decryptionEngine = new AvroDecryptionEngine<>(new LocalDecryptor(materialsProvider));
    }
}
