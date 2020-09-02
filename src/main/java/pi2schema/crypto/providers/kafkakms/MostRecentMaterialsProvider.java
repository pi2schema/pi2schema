package pi2schema.crypto.providers.kafkakms;

import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.DecryptingMaterialNotFoundException;
import pi2schema.crypto.materials.EncryptingMaterial;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterial;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import javax.crypto.spec.SecretKeySpec;


public class MostRecentMaterialsProvider implements EncryptingMaterialsProvider, DecryptingMaterialsProvider {

    private final KafkaSecretKeyStore kafkaSecretKeyStore;

    public MostRecentMaterialsProvider(KafkaSecretKeyStore kafkaSecretKeyStore) {
        this.kafkaSecretKeyStore = kafkaSecretKeyStore;
    }

    @Override
    public EncryptingMaterial encryptionKeysFor(String subjectId) {
        SubjectCryptographicMaterialAggregate materials = kafkaSecretKeyStore.retrieveOrCreateCryptoMaterialsFor(subjectId);

        return latestSecretKey(materials);
    }

    @Override
    public DecryptingMaterial decryptionKeysFor(String subjectId) { //todo add versioning for the decryption

        SubjectCryptographicMaterialAggregate materials = kafkaSecretKeyStore.existentMaterialsFor(subjectId)
                .orElseThrow(() -> new DecryptingMaterialNotFoundException(subjectId));

        return latestSecretKey(materials);
    }

    //todo rethink the list structure / versioning
    private SymmetricMaterial latestSecretKey(SubjectCryptographicMaterialAggregate materials) {
        int latestKeyIndex = materials.getMaterialsCount() - 1;
        SubjectCryptographicMaterial latestVersion = materials.getMaterialsList().get(latestKeyIndex);

        byte[] latestKey = latestVersion.getSymmetricKey().toByteArray();

        return new SymmetricMaterial(new SecretKeySpec(latestKey, latestVersion.getAlgorithm()));
    }
}
