package com.github.gustavomonarin.kafkagdpr.core.subject;

public interface SubjectIdentifierFieldDefinition<B> {

    String actualValueFrom(B buildingInstance);

}
