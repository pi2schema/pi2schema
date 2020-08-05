package com.github.gustavomonarin.kafkagdpr.core.kms;

public interface Decryptor {

    byte[] decrypt(String key, byte[] data);

}
