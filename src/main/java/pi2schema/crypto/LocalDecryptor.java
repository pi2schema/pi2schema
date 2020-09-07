package pi2schema.crypto;

import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class LocalDecryptor implements Decryptor {

    private final DecryptingMaterialsProvider provider;

    private final BiFunction<Cipher, byte[], CompletableFuture<byte[]>> decrypt =
            (Cipher cipher, byte[] bytes) -> CompletableFuture.supplyAsync(() -> {
                try {
                    return cipher.doFinal(bytes);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            });

    public LocalDecryptor(DecryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<byte[]> decrypt(String key, EncryptedData encryptedData) {
        final SecretKey decryptionKey = provider.decryptionKeysFor(key).getDecryptionKey();

        return CompletableFuture
                .supplyAsync(CipherSupplier.forDecryption(decryptionKey, encryptedData))
                .thenComposeAsync(cipher -> decrypt.apply(cipher, encryptedData.data()));
    }
}
