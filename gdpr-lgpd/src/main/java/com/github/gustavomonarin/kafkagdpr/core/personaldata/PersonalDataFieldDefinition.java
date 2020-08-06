package com.github.gustavomonarin.kafkagdpr.core.personaldata;

import com.github.gustavomonarin.kafkagdpr.core.encryption.Encryptor;

public interface PersonalDataFieldDefinition<T> extends PersonalDataValueProvider<T> {

    void swapToEncrypted(Encryptor encryptor, T buildingInstance);

}
