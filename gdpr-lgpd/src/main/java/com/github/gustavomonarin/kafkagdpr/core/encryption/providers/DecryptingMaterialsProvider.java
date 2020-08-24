package com.github.gustavomonarin.kafkagdpr.core.encryption.providers;

import com.github.gustavomonarin.kafkagdpr.core.encryption.materials.DecryptingMaterial;
import org.jetbrains.annotations.NotNull;

public interface DecryptingMaterialsProvider {

    DecryptingMaterial decryptionKeysFor(@NotNull String subjectId);


}
