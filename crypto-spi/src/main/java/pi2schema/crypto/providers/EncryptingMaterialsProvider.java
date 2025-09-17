package pi2schema.crypto.providers;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface EncryptingMaterialsProvider extends Closeable {
    CompletableFuture<EncryptionMaterial> encryptionKeysFor(String subjectId);

    @Override
    default void close() {}
}
