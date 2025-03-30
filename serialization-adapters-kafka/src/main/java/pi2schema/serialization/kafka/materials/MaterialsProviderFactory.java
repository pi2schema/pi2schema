package pi2schema.serialization.kafka.materials;

import java.util.Map;

/**
 * describe as temporary approach
 */
public interface MaterialsProviderFactory {
    Object create(Map<String, ?> configs);
}
