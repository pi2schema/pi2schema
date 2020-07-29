package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import com.acme.InvalidOneOfPersonalData;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject.SubjectIdentifierFieldDefinition;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OneOfPersonalDataFieldDefinitionTest {

    @Test
    void shouldThrowEncryptionTargetFieldNotFoundException() {

        Descriptor descriptor = InvalidOneOfPersonalData.MissingEncryptedPersonalDataField.getDescriptor();
        OneofDescriptor personalDataField = descriptor.getOneofs().get(0);
        SubjectIdentifierFieldDefinition subjectField = new SubjectIdentifierFieldDefinition(descriptor.getFields().get(0));


        assertThatExceptionOfType(EncryptionTargetFieldNotFoundException.class)
                .isThrownBy(() ->
                        new OneOfPersonalDataFieldDefinition(personalDataField, subjectField)
                )
                .withMessage("The personal data container com.acme.MissingEncryptedPersonalDataField.personal_data " +
                        "does not encapsulate a com.github.gustavomonarin.gdpr.EncryptedPersonalData while exact one is required");

    }

    @Test
    void shouldThrowTooManyEncryptionTargetFieldException() {

        Descriptor descriptor = InvalidOneOfPersonalData.MultipleEncryptedPersonalDataField.getDescriptor();
        OneofDescriptor personalDataField = descriptor.getOneofs().get(0);
        SubjectIdentifierFieldDefinition subjectField = new SubjectIdentifierFieldDefinition(descriptor.getFields().get(0));


        assertThatExceptionOfType(TooManyEncryptionTargetFieldsException.class)
                .isThrownBy(() ->
                        new OneOfPersonalDataFieldDefinition(personalDataField, subjectField)
                )
                .withMessage("The personal data container com.acme.MultipleEncryptedPersonalDataField.personal_data " +
                        "has 2 fields of type com.github.gustavomonarin.gdpr.EncryptedPersonalData while exact one is required");

    }

    @Test
    void hasPersonalData() {


    }
}