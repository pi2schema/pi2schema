package pi2schema.crypto.providers;

import pi2schema.crypto.materials.DecryptingMaterial;
import org.jetbrains.annotations.NotNull;

public interface DecryptingMaterialsProvider {

    DecryptingMaterial decryptionKeysFor(@NotNull String subjectId);


}
