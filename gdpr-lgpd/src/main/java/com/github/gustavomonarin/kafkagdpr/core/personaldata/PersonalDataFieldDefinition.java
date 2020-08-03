package com.github.gustavomonarin.kafkagdpr.core.personaldata;

public interface PersonalDataFieldDefinition<T> extends PersonalDataValueProvider<T> {

    void swapToEncrypted(PersonalDataEncryptor encryptor, T buildingInstance);

}
