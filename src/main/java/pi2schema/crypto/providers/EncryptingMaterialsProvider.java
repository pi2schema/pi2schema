package pi2schema.crypto.providers;

import pi2schema.crypto.materials.EncryptingMaterial;
import org.jetbrains.annotations.NotNull;

public interface EncryptingMaterialsProvider {

    EncryptingMaterial encryptionKeysFor(@NotNull String subjectId);

}
