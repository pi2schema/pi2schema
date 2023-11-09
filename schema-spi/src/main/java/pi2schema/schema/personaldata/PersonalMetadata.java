package pi2schema.schema.personaldata;

import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Describes a personal identifiable information for a whole object defining possible operations
 * for the object and its fields.
 *
 * @param <T> The type of the object
 */
public interface PersonalMetadata<T> {

    boolean requiresEncryption();

    Stream<CompletableFuture<Void>> swapToEncrypted(Encryptor encryptor, T decryptedInstance);

    Stream<CompletableFuture<Void>> swapToDecrypted(Decryptor decryptor, T DecryptedInstance);

}
