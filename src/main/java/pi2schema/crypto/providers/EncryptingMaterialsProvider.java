package pi2schema.crypto.providers;

import pi2schema.crypto.materials.EncryptingMaterial;
import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.materials.SymmetricMaterial;

import java.util.concurrent.CompletableFuture;

public interface EncryptingMaterialsProvider {

    CompletableFuture<SymmetricMaterial> encryptionKeysFor(@NotNull String subjectId);
}
