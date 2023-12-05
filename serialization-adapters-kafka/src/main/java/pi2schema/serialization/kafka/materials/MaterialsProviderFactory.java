package pi2schema.serialization.kafka.materials;

import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

/**
 * describe as temporary approach
 */
public interface MaterialsProviderFactory {
    Object create(Map<String, ?> configs);

}
