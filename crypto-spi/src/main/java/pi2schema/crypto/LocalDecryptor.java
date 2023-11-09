package pi2schema.crypto;

import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class LocalDecryptor implements Decryptor {

    private final DecryptingMaterialsProvider provider;

    public LocalDecryptor(DecryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<ByteBuffer> decrypt(String key, EncryptedData encryptedData) {
        return provider
            .decryptionKeysFor(key)
            .thenApply(SymmetricMaterial::getDecryptionKey)
            .thenCompose(decryptionKey ->
                CompletableFuture.supplyAsync(Ciphers.forDecryption(decryptionKey, encryptedData))
            )
            .thenComposeAsync(cipher -> Ciphers.apply(cipher, encryptedData.data()));
    }
}
