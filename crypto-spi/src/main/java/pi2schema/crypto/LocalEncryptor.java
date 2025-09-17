package pi2schema.crypto;

import com.google.crypto.tink.aead.AeadConfig;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

/**
 * Tink-based implementation that delegates envelope cryptography to the MaterialsProvider.
 * The MaterialsProvider handles all KEK/DEK operations and returns ready-to-use Aead primitives.
 */
public class LocalEncryptor implements Encryptor {

    private final EncryptingMaterialsProvider provider;

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize Tink AEAD", e);
        }
    }

    public LocalEncryptor(EncryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<EncryptedData> encrypt(String subjectId, ByteBuffer data) {
        return provider
            .encryptionKeysFor(subjectId)
            .thenCompose(material ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // Encrypt the plaintext directly with the provided DEK
                        byte[] plaintextBytes = new byte[data.remaining()];
                        data.duplicate().get(plaintextBytes);
                        byte[] encryptedData = material.dataEncryptionKey().encrypt(plaintextBytes, new byte[0]);

                        return new EncryptedData(
                            ByteBuffer.wrap(encryptedData),
                            ByteBuffer.wrap(material.encryptedDataKey()),
                            material.encryptionContext()
                        );
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException("Encryption failed", e);
                    }
                })
            );
    }
}
