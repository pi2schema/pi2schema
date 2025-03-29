package com.acme;

import java.util.UUID;

public class InvalidUserWithMultipleSubjectIdentifiersFixture {

    public static InvalidUserWithMultipleSubjectIdentifiers.Builder johnDoe(){
        return InvalidUserWithMultipleSubjectIdentifiers.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setFavoriteNumber(42)
                .setEmail("john@doe.com");
    }
}
