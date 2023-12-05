package pi2schema.serialization.kafka.materials;

import java.util.Map;

public class InMemoryMaterialsProvider implements MaterialsProviderFactory {

    @Override
    public Object create(Map<String, ?> configs) {
        return new InMemoryMaterialsProvider();
    }
}