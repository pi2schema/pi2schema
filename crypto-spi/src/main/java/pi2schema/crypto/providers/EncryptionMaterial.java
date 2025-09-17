package pi2schema.crypto.providers;

import com.google.crypto.tink.Aead;

/**
 * Represents an encryption result from the MaterialsProvider containing both
 * the ready-to-use DEK and the encrypted DEK for storage.
 */
public record EncryptionMaterial(Aead dataEncryptionKey, byte[] encryptedDataKey, String encryptionContext) {}
