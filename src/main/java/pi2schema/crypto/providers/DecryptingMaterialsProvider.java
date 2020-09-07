package pi2schema.crypto.providers;

import pi2schema.crypto.materials.DecryptingMaterial;
import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.materials.SymmetricMaterial;

import java.util.concurrent.CompletableFuture;

public interface DecryptingMaterialsProvider {

    CompletableFuture<SymmetricMaterial> decryptionKeysFor(@NotNull String subjectId);


}
