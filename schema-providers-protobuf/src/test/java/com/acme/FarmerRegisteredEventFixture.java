package com.acme;

import pi2schema.schema.providers.protobuf.personaldata.OneOfPersonalDataFieldDefinition;
import pi2schema.schema.providers.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;

import static com.acme.FarmerRegisteredEventOuterClass.ContactInfo;
import static com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;

public class FarmerRegisteredEventFixture {

    public static FarmerRegisteredEvent.Builder johnDoe() {
        return FarmerRegisteredEvent.newBuilder()
                .setContactInfo(
                        ContactInfo.newBuilder()
                                .setName("John Doe")
                                .setEmail("john.doe@acme.com")
                );
    }

    public static OneOfPersonalDataFieldDefinition personalDataFieldDefinition() {
        var event = FarmerRegisteredEvent.newBuilder();

        var personalData = event.getDescriptorForType().getOneofs().get(0);
        var uuidFieldDef = event.getDescriptorForType().getFields().get(0);
        var subjectId = new ProtobufSubjectIdentifierFieldDefinition(uuidFieldDef);

        return new OneOfPersonalDataFieldDefinition(personalData, subjectId);
    }
}
