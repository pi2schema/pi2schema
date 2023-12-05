package pi2schema.serialization.kafka;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import pi2schema.serialization.kafka.materials.MaterialsProviderFactory;

import java.util.Map;

public class PiiAwareInterceptorConfig extends AbstractConfig {

    private static final ConfigDef CONFIG;

    public static final String PERSONAL_METADATA_PROVIDER_CONFIG = "pi2schema.personal.metadata.provider";
    public static final String PERSONAL_METADATA_PROVIDER_CONFIG_DOC =
        "The Personal metadata provider capable of ispect the ";

    public static final String MATERIALS_PROVIDER_CONFIG = "pi2schema.personal.materials.provider";
    public static final String MATERIALS_PROVIDER_CONFIG_DOC = "The material metadata provider capable of ispect the ";

    // TODO: improve documentation, describe currently supported classes.

    static {
        CONFIG =
            new ConfigDef()
                .define(
                    PERSONAL_METADATA_PROVIDER_CONFIG,
                    ConfigDef.Type.CLASS,
                    ConfigDef.Importance.HIGH,
                    PERSONAL_METADATA_PROVIDER_CONFIG_DOC
                )
                .define(
                    MATERIALS_PROVIDER_CONFIG,
                    ConfigDef.Type.CLASS,
                    MaterialsProviderFactory.class,
                    ConfigDef.Importance.MEDIUM,
                    MATERIALS_PROVIDER_CONFIG_DOC
                );
    }

    public PiiAwareInterceptorConfig(Map<?, ?> originals) {
        super(CONFIG, originals);
    }
}
