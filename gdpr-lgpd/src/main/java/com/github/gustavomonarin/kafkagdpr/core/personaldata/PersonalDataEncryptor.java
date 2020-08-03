package com.github.gustavomonarin.kafkagdpr.core.personaldata;

public interface PersonalDataEncryptor {

    <E> E encrypt(String subject, byte[] data);

}
