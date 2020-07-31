package com.github.gustavomonarin.kafkagdpr.core.personaldata;

public interface PersonalDataFieldDefinition<B> {

    void swapToEncrypted(B buildingInstance);

}
