package pi2schema.crypto.providers.kafkakms;

import pi2schema.crypto.materials.MissingCryptoMaterialsException;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;

import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.SecretKeySpec;

public class MostRecentMaterialsProvider implements EncryptingMaterialsProvider, DecryptingMaterialsProvider {

    private final KafkaSecretKeyStore kafkaSecretKeyStore;

    public MostRecentMaterialsProvider(KafkaSecretKeyStore kafkaSecretKeyStore) {
        this.kafkaSecretKeyStore = kafkaSecretKeyStore;
    }

    @Override
    public CompletableFuture<SymmetricMaterial> encryptionKeysFor(String subjectId) {
        return kafkaSecretKeyStore.retrieveOrCreateCryptoMaterialsFor(subjectId).thenApply(this::latestSecretKey);
    }

    //TODO add versioning for the decryption
    @Override
    public CompletableFuture<SymmetricMaterial> decryptionKeysFor(String subjectId) {
        return kafkaSecretKeyStore
            .existentMaterialsFor(subjectId)
            .thenApply(materials -> {
                if (materials == null) throw new MissingCryptoMaterialsException(subjectId);

                return latestSecretKey(materials);
            });
    }

    //todo rethink the list structure / versioning
    private SymmetricMaterial latestSecretKey(SubjectCryptographicMaterialAggregate materials) {
        int latestKeyIndex = materials.getMaterialsCount() - 1;
        var latestVersion = materials.getMaterialsList().get(latestKeyIndex);

        var latestKey = latestVersion.getSymmetricKey().toByteArray();

        return new SymmetricMaterial(new SecretKeySpec(latestKey, latestVersion.getAlgorithm()));
    }

    @Override
    public void close() {
        kafkaSecretKeyStore.close();
    }
}
