package pi2schema.serialization.kafka.materials;

import pi2schema.crypto.providers.kafkakms.KafkaSecretKeyStore;
import pi2schema.crypto.providers.kafkakms.MostRecentMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import java.util.Map;

class KafkaMaterialsProvider implements MaterialsProviderFactory {

    @Override
    public Object create(Map<String, ?> configs) {
        return new MostRecentMaterialsProvider(new KafkaSecretKeyStore(KeyGen.aes256(), configs));
    }
}
