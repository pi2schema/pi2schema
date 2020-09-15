package pi2schema.crypto.providers;

import pi2schema.crypto.materials.SymmetricMaterial;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface EncryptingMaterialsProvider extends Closeable {

    CompletableFuture<SymmetricMaterial> encryptionKeysFor(String subjectId);

    @Override
    default void close() { }
}
