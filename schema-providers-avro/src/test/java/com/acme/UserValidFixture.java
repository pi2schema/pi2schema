package com.acme;

import java.util.UUID;

public class UserValidFixture {

    public static UserValid.Builder johnDoe(){
        return UserValid.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setFavoriteNumber(42)
                .setEmail("john@doe.com");
    }
}
