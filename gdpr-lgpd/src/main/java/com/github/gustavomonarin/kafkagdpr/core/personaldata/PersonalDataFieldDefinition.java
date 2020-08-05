package com.github.gustavomonarin.kafkagdpr.core.personaldata;

import com.github.gustavomonarin.kafkagdpr.core.kms.Encryptor;

public interface PersonalDataFieldDefinition<T> extends PersonalDataValueProvider<T> {

    void swapToEncrypted(Encryptor encryptor, T buildingInstance);

}
