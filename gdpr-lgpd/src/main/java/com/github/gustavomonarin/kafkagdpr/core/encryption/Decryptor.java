package com.github.gustavomonarin.kafkagdpr.core.encryption;

public interface Decryptor {

    byte[] decrypt(String key, EncryptedData data);

}
