package com.github.gustavomonarin.kafkagdpr.core.encryption;

public interface Encryptor {


    EncryptedData encrypt(String subjectId, byte[] data);


}
