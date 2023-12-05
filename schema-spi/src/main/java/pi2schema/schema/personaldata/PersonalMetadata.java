package pi2schema.schema.personaldata;

import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;

/**
 * Describes a personal identifiable information for a whole object defining possible operations
 * for the object and its fields.
 *
 * @param <T> The type of the object
 */
public interface PersonalMetadata<T> {
    boolean requiresEncryption();

    T swapToEncrypted(Encryptor encryptor, T decryptedInstance);

    T swapToDecrypted(Decryptor decryptor, T encryptedInstance);
}
