package pi2schema.crypto;

import com.google.crypto.tink.aead.AeadConfig;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

/**
 * Tink-based implementation that delegates envelope cryptography to the MaterialsProvider.
 * The MaterialsProvider handles KEK/DEK decryption and returns ready-to-use Aead primitives.
 */
public class LocalDecryptor implements Decryptor {

    private final DecryptingMaterialsProvider provider;

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize Tink AEAD", e);
        }
    }

    public LocalDecryptor(DecryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<ByteBuffer> decrypt(String key, EncryptedData encryptedData) {
        // Extract bytes from the read-only ByteBuffer safely
        ByteBuffer encryptedKeyBuffer = encryptedData.encryptedDataKey();
        byte[] encryptedKeyBytes = new byte[encryptedKeyBuffer.remaining()];
        encryptedKeyBuffer.duplicate().get(encryptedKeyBytes);

        return provider
            .decryptionKeysFor(key, encryptedKeyBytes, encryptedData.keysetHandle())
            .thenCompose(dek ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // Decrypt the data directly with the provided DEK
                        byte[] encryptedDataBytes = new byte[encryptedData.data().remaining()];
                        encryptedData.data().duplicate().get(encryptedDataBytes);

                        byte[] decryptedData = dek.decrypt(encryptedDataBytes, new byte[0]);

                        return ByteBuffer.wrap(decryptedData).asReadOnlyBuffer();
                    } catch (Exception e) {
                        throw new RuntimeException("Decryption failed", e);
                    }
                })
            );
    }
}
