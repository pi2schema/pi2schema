package pi2schema.crypto.providers.kafkakms;

import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.materials.EncryptingMaterial;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import piischema.kms.KafkaProvider.SubjectCryptographicMaterial;
import piischema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;


public class MostRecentMaterialsProvider implements EncryptingMaterialsProvider {

    private final KafkaKeyStore kafkaKeyStore;

    public MostRecentMaterialsProvider(Map<String, Object> configs) {
        kafkaKeyStore = new KafkaKeyStore(configs);
    }

    @Override
    public EncryptingMaterial encryptionKeysFor(@NotNull String subjectId) {
        SubjectCryptographicMaterialAggregate materials = kafkaKeyStore.cryptoMaterialsFor(subjectId);

        int latestKeyIndex = materials.getMaterialsCount() - 1;
        SubjectCryptographicMaterial latestVersion = materials.getMaterialsList().get(latestKeyIndex);

        byte[] latestKey = latestVersion.getSymmetricKey().toByteArray();
        SecretKey secretKeySpec = new SecretKeySpec(latestKey, latestVersion.getAlgorithm());

        return new SymmetricMaterial(secretKeySpec);
    }


}
