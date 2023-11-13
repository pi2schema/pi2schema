package pi2schema.serialization.kafka;

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
import pi2schema.schema.personaldata.PersonalMetadataProvider;

import java.util.Map;
import java.util.stream.Collectors;

import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public final class KafkaGdprAwareConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

    private DecryptingMaterialsProvider materialsProvider;
    private LocalDecryptor decryptor;
    private PersonalMetadataProvider<V> provider;

    public KafkaGdprAwareConsumerInterceptor() {}

    public KafkaGdprAwareConsumerInterceptor(DecryptingMaterialsProvider materialsProvider) {
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
                            provider.forType(record.value()).swapToDecrypted(decryptor, record.value()),
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
        if (materialsProvider != null) materialsProvider.close();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        materialsProvider = new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
        decryptor = new LocalDecryptor(materialsProvider);
        provider =
            new PiiAwareInterceptorConfig(configs)
                .getConfiguredInstance(PERSONAL_METADATA_PROVIDER_CONFIG, PersonalMetadataProvider.class);
    }
}
