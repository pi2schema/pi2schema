package pi2schema.serialization.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import pi2schema.crypto.LocalEncryptor;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.schema.SchemaProvider;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.serialization.kafka.materials.MaterialsProviderFactory;

import java.util.Map;

import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public final class KafkaGdprAwareProducerInterceptor<K, V, S> implements ProducerInterceptor<K, V> {

    private SchemaProvider<S> schemaProvider;
    private EncryptingMaterialsProvider materialsProvider;
    private LocalEncryptor localEncryptor;

    private PersonalMetadataProvider<V, S> metadataProvider;

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record == null || record.value() == null) return record;

        S schema = schemaProvider.schemaFor(record.value());

        return new ProducerRecord<>(
            record.topic(),
            record.partition(),
            record.timestamp(),
            record.key(),
            metadataProvider.forSchema(schema).swapToEncrypted(localEncryptor, record.value())
        );
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}

    @Override
    public void close() {
        if (materialsProvider != null) materialsProvider.close();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        var piiAwareInterceptorConfig = new PiiAwareInterceptorConfig(configs);

        schemaProvider =
            piiAwareInterceptorConfig.getConfiguredInstance(
                PiiAwareInterceptorConfig.SCHEMA_DEFINITION_PROVIDER_CONFIG,
                SchemaProvider.class
            );

        metadataProvider =
            piiAwareInterceptorConfig.getConfiguredInstance(
                PERSONAL_METADATA_PROVIDER_CONFIG,
                PersonalMetadataProvider.class
            );

        materialsProvider =
            (EncryptingMaterialsProvider) piiAwareInterceptorConfig
                .getConfiguredInstance(MATERIALS_PROVIDER_CONFIG, MaterialsProviderFactory.class)
                .create(configs);

        localEncryptor = new LocalEncryptor(materialsProvider);
    }
}
