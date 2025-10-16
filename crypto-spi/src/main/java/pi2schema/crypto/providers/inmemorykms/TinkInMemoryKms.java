package pi2schema.crypto.providers.inmemorykms;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tink-based in-memory key management system that handles envelope cryptography internally.
 * This implementation maintains the crypto shredding architecture where each subject identifier
 * gets its own KEK (Key Encryption Key). The KEK is used internally to encrypt/decrypt DEKs,
 * but is never exposed outside this provider.
 */
public class TinkInMemoryKms implements EncryptingMaterialsProvider, DecryptingMaterialsProvider {

    private final Map<String, Aead> kekStore = new HashMap<>();

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize Tink AEAD", e);
        }
    }

    @Override
    public CompletableFuture<EncryptionMaterial> encryptionKeysFor(String subjectId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get or create KEK for this subject
                Aead kek = getOrCreateKek(subjectId);

                // Generate a fresh DEK for this encryption operation
                KeysetHandle dekHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM);
                Aead dek = dekHandle.getPrimitive(Aead.class);

                // Serialize and encrypt the DEK with the KEK using proper Tink API
                String serializedDek = TinkJsonProtoKeysetFormat.serializeKeyset(
                    dekHandle,
                    com.google.crypto.tink.InsecureSecretKeyAccess.get()
                );
                byte[] encryptedDek = kek.encrypt(serializedDek.getBytes(), subjectId.getBytes());

                return new EncryptionMaterial(dek, encryptedDek, subjectId);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("Failed to create encryption material for subject: " + subjectId, e);
            }
        });
    }

    @Override
    public CompletableFuture<Aead> decryptionKeysFor(
        String subjectId,
        byte[] encryptedDataKey,
        String encryptionContext
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get the KEK for this subject
                Aead kek = kekStore.get(subjectId);
                if (kek == null) {
                    throw new IllegalArgumentException("No KEK found for subject: " + subjectId);
                }

                // Decrypt the DEK using the KEK
                byte[] serializedDek = kek.decrypt(encryptedDataKey, encryptionContext.getBytes());

                // Reconstruct the DEK keyset handle using proper Tink API
                String dekJson = new String(serializedDek);
                KeysetHandle dekHandle = TinkJsonProtoKeysetFormat.parseKeyset(
                    dekJson,
                    com.google.crypto.tink.InsecureSecretKeyAccess.get()
                );
                return dekHandle.getPrimitive(Aead.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create decryption material for subject: " + subjectId, e);
            }
        });
    }

    /**
     * Enables crypto shredding by removing the KEK for a specific subject.
     * All data encrypted with this subject's key becomes permanently unrecoverable.
     */
    public boolean deleteKeyMaterial(String subjectId) {
        synchronized (this) {
            return kekStore.remove(subjectId) != null;
        }
    }

    /**
     * Returns the number of subject KEKs currently stored.
     */
    public int getKeyCount() {
        synchronized (this) {
            return kekStore.size();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            kekStore.clear();
        }
    }

    private Aead getOrCreateKek(String subjectId) {
        synchronized (this) {
            return kekStore.computeIfAbsent(subjectId, this::createNewKek);
        }
    }

    private Aead createNewKek(String subjectId) {
        try {
            // Generate a new KEK for this subject - this KEK never leaves this provider
            KeysetHandle kekHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM);
            return kekHandle.getPrimitive(Aead.class);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to create KEK for subject: " + subjectId, e);
        }
    }
}
