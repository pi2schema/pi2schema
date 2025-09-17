package pi2schema.serialization.kafka.materials;

import pi2schema.crypto.providers.inmemorykms.TinkInMemoryKms;

import java.util.Map;

/** An in memory materials provider for testing purposes. */
public class InMemoryMaterialsProvider implements MaterialsProviderFactory {

    public static final TinkInMemoryKms SINGLETON = new TinkInMemoryKms();

    @Override
    public Object create(Map<String, ?> configs) {
        return SINGLETON;
    }
}
