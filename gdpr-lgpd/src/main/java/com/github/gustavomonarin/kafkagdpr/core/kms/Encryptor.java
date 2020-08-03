package com.github.gustavomonarin.kafkagdpr.core.kms;

public interface Encryptor {


    byte[] encrypt(String subjectId, byte[] data);


}
