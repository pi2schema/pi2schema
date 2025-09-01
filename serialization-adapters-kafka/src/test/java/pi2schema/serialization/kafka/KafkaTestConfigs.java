package pi2schema.serialization.kafka;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Map.Entry;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public class KafkaTestConfigs {

    private static final Entry<String, String> materialsProviderConfig = Map.entry(
        MATERIALS_PROVIDER_CONFIG,
        "pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider"
    );
    private static final Entry<String, String> protobufSchemaDefinitionProviderConfig = Map.entry(
        "pi2schema.schema.definition.provider",
        "pi2schema.schema.providers.protobuf.LocalProtobufSchemaProvider"
    );
    private static final Entry<String, String> protobufMetadataProviderConfig = Map.entry(
        PERSONAL_METADATA_PROVIDER_CONFIG,
        "pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider"
    );
    private static final Entry<String, String> avroSchemaDefinitionProviderConfig = Map.entry(
        "pi2schema.schema.definition.provider",
        "pi2schema.schema.providers.avro.LocalAvroSchemaProvider"
    );
    private static final Entry<String, String> avroMetadataProviderConfig = Map.entry(
        PERSONAL_METADATA_PROVIDER_CONFIG,
        "pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider"
    );
    private static final Entry<String, String> jsonSchemaDefinitionProviderConfig = Map.entry(
        "pi2schema.schema.definition.provider",
        "pi2schema.serialization.kafka.jsonschema.KafkaJsonSchemaProvider"
    );
    private static final Entry<String, String> jsonSchemaMetadataProviderConfig = Map.entry(
        PERSONAL_METADATA_PROVIDER_CONFIG,
        "pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider"
    );

    private static final Entry<String, String> schemaRegistryUrlConfig = Map.entry(
        SCHEMA_REGISTRY_URL_CONFIG,
        "mock://test-scope"
    );

    @NotNull
    static Map<String, String> configsForAvro() {
        return Map.ofEntries(materialsProviderConfig, avroMetadataProviderConfig, avroSchemaDefinitionProviderConfig);
    }

    @NotNull
    static Map<String, String> configsForPotobuffer() {
        return Map.ofEntries(
            materialsProviderConfig,
            protobufMetadataProviderConfig,
            protobufSchemaDefinitionProviderConfig
        );
    }

    @NotNull
    static Map<String, Object> configsForJsonSchema() {
        return Map.ofEntries(
            schemaRegistryUrlConfig,
            materialsProviderConfig,
            jsonSchemaMetadataProviderConfig,
            jsonSchemaDefinitionProviderConfig
        );
    }
}
