package pi2schema.serialization.kafka;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public class KafkaTestConfigs {


    private static final Map.Entry<String, String> materialsProviderConfig = Map.entry(
            MATERIALS_PROVIDER_CONFIG, "pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider");
    private static final Map.Entry<String, String> protobufMetadataProviderConfig = Map.entry(
            PERSONAL_METADATA_PROVIDER_CONFIG, "pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider");
    private static final Map.Entry<String, String> avroMetadataProviderConfig = Map.entry(
            PERSONAL_METADATA_PROVIDER_CONFIG, "pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider");


    @NotNull
    static Map<String, String> configsForAvro() {
        return Map.ofEntries(materialsProviderConfig, avroMetadataProviderConfig);
    }


    @NotNull
    static Map<String, String> configsForPotobuffer() {
        return Map.ofEntries(materialsProviderConfig, protobufMetadataProviderConfig);
    }
}
