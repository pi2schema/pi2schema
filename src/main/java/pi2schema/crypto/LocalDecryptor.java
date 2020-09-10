package pi2schema.crypto;

import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import javax.crypto.Cipher;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class LocalDecryptor implements Decryptor {

    private final DecryptingMaterialsProvider provider;

    private final BiFunction<Cipher, byte[], CompletableFuture<byte[]>> decrypt = CipherSupplier.EXECUTOR;

    public LocalDecryptor(DecryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<byte[]> decrypt(String key, EncryptedData encryptedData) {
        return provider.decryptionKeysFor(key)
                .thenApply(SymmetricMaterial::getDecryptionKey)
                .thenCompose(decryptionKey ->
                        CompletableFuture.supplyAsync(
                                CipherSupplier.forDecryption(decryptionKey, encryptedData)))
                .thenComposeAsync(cipher -> decrypt.apply(cipher, encryptedData.data()));
    }
}
