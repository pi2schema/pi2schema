package com.github.gustavomonarin.kafkagdpr.core.encryption.materials;

import javax.crypto.SecretKey;

public interface DecryptingMaterial {

    SecretKey getDecryptionKey();


}
