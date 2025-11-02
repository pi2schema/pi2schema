package pi2schema.serialization.kafka;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import pi2schema.serialization.kafka.materials.VaultMaterialsProvider;

import java.util.Map;

public class PiiAwareInterceptorConfig extends AbstractConfig {

    private static final ConfigDef CONFIG;

    public static final String SCHEMA_DEFINITION_PROVIDER_CONFIG = "pi2schema.schema.definition.provider";
    public static final String SCHEMA_DEFINITION_PROVIDER_CONFIG_DOC =
        "The Schema Definition provider capable of fetching the schema definition for a given business object";

    public static final String PERSONAL_METADATA_PROVIDER_CONFIG = "pi2schema.personal.metadata.provider";
    public static final String PERSONAL_METADATA_PROVIDER_CONFIG_DOC =
        "The Personal metadata provider capable of inspecting the personal data from the message";

    public static final String MATERIALS_PROVIDER_CONFIG = "pi2schema.personal.materials.provider";
    public static final String MATERIALS_PROVIDER_CONFIG_DOC =
        "The material provider factory capable of creating the materials provider for cryptographic operations";

    // TODO: improve documentation, describe currently supported classes.

    static {
        CONFIG =
            new ConfigDef()
                .define(
                    SCHEMA_DEFINITION_PROVIDER_CONFIG,
                    ConfigDef.Type.CLASS,
                    ConfigDef.Importance.HIGH,
                    SCHEMA_DEFINITION_PROVIDER_CONFIG_DOC
                )
                .define(
                    PERSONAL_METADATA_PROVIDER_CONFIG,
                    ConfigDef.Type.CLASS,
                    ConfigDef.Importance.HIGH,
                    PERSONAL_METADATA_PROVIDER_CONFIG_DOC
                )
                .define(
                    MATERIALS_PROVIDER_CONFIG,
                    ConfigDef.Type.CLASS,
                    VaultMaterialsProvider.class,
                    ConfigDef.Importance.MEDIUM,
                    MATERIALS_PROVIDER_CONFIG_DOC
                );
    }

    public PiiAwareInterceptorConfig(Map<?, ?> originals) {
        super(CONFIG, originals);
    }
}
