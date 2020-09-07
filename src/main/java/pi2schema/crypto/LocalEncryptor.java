package pi2schema.crypto;

import pi2schema.crypto.providers.EncryptingMaterialsProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Default implementation performing the actual encryption / decryption using the standard Java building blocks for cryptography.
 * <p>
 * Relies on {@link EncryptingMaterialsProvider} for the Secret retrieval and creation used for the cryptography.
 */
public class LocalEncryptor implements Encryptor {

    // https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher
    // Might consider extracting these values somewhere else or make it configurable
    private static final String MODE = "CBC"; //avoiding default aes ECB
    private static final String PADDING = "PKCS5Padding";

    private final EncryptingMaterialsProvider provider;

    private final BiFunction<Cipher, byte[], CompletableFuture<byte[]>> encrypt =
            (Cipher c, byte[] bytes) -> CompletableFuture.supplyAsync(() -> {
                try {
                    c.update(bytes);
                    return c.doFinal();
                } catch (IllegalBlockSizeException | BadPaddingException e) {
                    throw new RuntimeException(e);
                }
            });

    public LocalEncryptor(EncryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<EncryptedData> encrypt(String subjectId, byte[] data) {
        final SecretKey encryptionKey = provider.encryptionKeysFor(subjectId).getEncryptionKey();
        final String transformation = String.format("%s/%s/%s", encryptionKey.getAlgorithm(), MODE, PADDING);

        return CompletableFuture
                .supplyAsync(CipherSupplier.forEncryption(encryptionKey, transformation))
                .thenComposeAsync(cipher ->
                        encrypt.apply(cipher, data)
                                .thenApplyAsync(encryptedData ->
                                        new EncryptedData(
                                                encryptedData,
                                                transformation,
                                                new IvParameterSpec(cipher.getIV())
                                        )));
    }

}
