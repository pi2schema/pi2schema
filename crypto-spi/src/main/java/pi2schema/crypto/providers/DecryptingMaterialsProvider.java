package pi2schema.crypto.providers;

import com.google.crypto.tink.Aead;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface DecryptingMaterialsProvider extends Closeable {
    /**
     * Decrypts the provided encrypted DEK using the subject's KEK and returns
     * the ready-to-use DEK as an Aead primitive.
     *
     * @param subjectId the subject identifier to locate the appropriate KEK
     * @param encryptedDataKey the encrypted DEK to decrypt
     * @param encryptionContext context or metadata from encryption
     * @return Aead primitive containing the decrypted DEK
     */
    CompletableFuture<Aead> decryptionKeysFor(String subjectId, byte[] encryptedDataKey, String encryptionContext);

    @Override
    default void close() {}
}
