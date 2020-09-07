package pi2schema.crypto.providers.kafkakms;

import pi2schema.crypto.materials.DecryptingMaterialNotFoundException;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterial;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.CompletableFuture;

public class MostRecentMaterialsProvider implements EncryptingMaterialsProvider, DecryptingMaterialsProvider {

    private final KafkaSecretKeyStore kafkaSecretKeyStore;

    public MostRecentMaterialsProvider(KafkaSecretKeyStore kafkaSecretKeyStore) {
        this.kafkaSecretKeyStore = kafkaSecretKeyStore;
    }

    @Override
    public CompletableFuture<SymmetricMaterial> encryptionKeysFor(String subjectId) {
        return kafkaSecretKeyStore
                .retrieveOrCreateCryptoMaterialsFor(subjectId)
                .thenApply(this::latestSecretKey);
    }

    //TODO add versioning for the decryption
    @Override
    public CompletableFuture<SymmetricMaterial> decryptionKeysFor(String subjectId) {
        try {
        return kafkaSecretKeyStore
                .existentMaterialsFor(subjectId)
                .thenApply(this::latestSecretKey);
        } catch (NullPointerException e) {
            throw new DecryptingMaterialNotFoundException(subjectId);
        }
    }

    //todo rethink the list structure / versioning
    private SymmetricMaterial latestSecretKey(SubjectCryptographicMaterialAggregate materials) {
        int latestKeyIndex = materials.getMaterialsCount() - 1;
        SubjectCryptographicMaterial latestVersion = materials.getMaterialsList().get(latestKeyIndex);

        byte[] latestKey = latestVersion.getSymmetricKey().toByteArray();

        return new SymmetricMaterial(new SecretKeySpec(latestKey, latestVersion.getAlgorithm()));
    }
}
