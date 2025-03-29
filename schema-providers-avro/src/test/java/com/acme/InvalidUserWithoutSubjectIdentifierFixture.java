package com.acme;

import java.util.UUID;

public class InvalidUserWithoutSubjectIdentifierFixture {

    public static InvalidUserWithoutSubjectIdentifier.Builder johnDoe(){
        return InvalidUserWithoutSubjectIdentifier.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setFavoriteNumber(42)
                .setEmail("john@doe.com");
    }
}
