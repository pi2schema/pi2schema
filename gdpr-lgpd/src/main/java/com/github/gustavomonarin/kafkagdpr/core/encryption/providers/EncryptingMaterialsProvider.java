package com.github.gustavomonarin.kafkagdpr.core.encryption.providers;

import com.github.gustavomonarin.kafkagdpr.core.encryption.materials.EncryptingMaterial;
import org.jetbrains.annotations.NotNull;

public interface EncryptingMaterialsProvider {

    EncryptingMaterial encryptionKeysFor(@NotNull String subjectId);

}
