package com.acme;

import static com.github.gustavomonarin.gdpr.FarmerRegisteredEventOuterClass.ContactInfo;
import static com.github.gustavomonarin.gdpr.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;

public class FarmerRegisteredEventFixture {

    public static FarmerRegisteredEvent.Builder johnDoe() {
        return FarmerRegisteredEvent.newBuilder()
                .setContactInfo(
                        ContactInfo.newBuilder()
                                .setName("John Doe")
                                .setEmail("john.doe@acme.com")
                );
    }
}
