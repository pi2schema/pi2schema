package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.InvalidOneOfPersonalData;
import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.gdpr.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.github.gustavomonarin.kafkagdpr.core.encryption.Decryptor;
import com.github.gustavomonarin.kafkagdpr.core.encryption.Encryptor;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.EncryptionTargetFieldNotFoundException;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.TooManyEncryptionTargetFieldsException;
import com.github.gustavomonarin.kafkagdpr.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OneOfPersonalDataFieldDefinitionTest {

    @Test
    void shouldThrowEncryptionTargetFieldNotFoundException() {

        Descriptor descriptor = InvalidOneOfPersonalData.MissingEncryptedPersonalDataField.getDescriptor();
        OneofDescriptor personalDataField = descriptor.getOneofs().get(0);
        ProtobufSubjectIdentifierFieldDefinition subjectField = new ProtobufSubjectIdentifierFieldDefinition(descriptor.getFields().get(0));


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
        ProtobufSubjectIdentifierFieldDefinition subjectField = new ProtobufSubjectIdentifierFieldDefinition(descriptor.getFields().get(0));


        assertThatExceptionOfType(TooManyEncryptionTargetFieldsException.class)
                .isThrownBy(() ->
                        new OneOfPersonalDataFieldDefinition(personalDataField, subjectField)
                )
                .withMessage("The personal data container com.acme.MultipleEncryptedPersonalDataField.personal_data " +
                        "has 2 fields of type com.github.gustavomonarin.gdpr.EncryptedPersonalData while exact one is required");

    }

    @Test
    void targetField() {
        OneOfPersonalDataFieldDefinition personalData = FarmerRegisteredEventFixture.personalDataFieldDefinition();

        Descriptors.FieldDescriptor encryptionTargetField = personalData.encryptionTargetField();

        assertThat(encryptionTargetField.getFullName())
                .isEqualTo("com.github.gustavomonarin.gdpr.FarmerRegisteredEvent.encryptedPersonalData");

        assertThat(encryptionTargetField.getNumber())
                .isEqualTo(3);
    }

    @Test
    void personalDataField() {
        OneOfPersonalDataFieldDefinition personalData = FarmerRegisteredEventFixture.personalDataFieldDefinition();

        Descriptors.FieldDescriptor peresonalDataTargetField = personalData.personalDataTargetField(2);

        assertThat(peresonalDataTargetField.getFullName())
                .isEqualTo("com.github.gustavomonarin.gdpr.FarmerRegisteredEvent.contact_info");
    }

    @Test
    void swapToEncryptedShouldIgnoreEncryptionWhenPersonalDataIsNotSet() {

        // given
        String uuid = UUID.randomUUID().toString();
        FarmerRegisteredEvent.Builder event = FarmerRegisteredEvent.newBuilder().setUuid(uuid);

        OneOfPersonalDataFieldDefinition personalDataFieldDef = FarmerRegisteredEventFixture.personalDataFieldDefinition();

        Encryptor encryptor = (subject, data) -> {
            throw new AssertionFailedError("Should not encrypt anything, as there is no personal data set");
        };

        FarmerRegisteredEvent.Builder expected = event.clone(); //expected with no changes

//        when
        personalDataFieldDef.swapToEncrypted(encryptor, event);

//        then
        assertThat(event.build()).isEqualTo(expected.build());
    }

    @Test
    void swapToEncrypted() {

    }

    @Test
    void swapToDecryted() {
        //given
        String uuid = UUID.randomUUID().toString();
        ByteString encrypted = ByteString.copyFrom("encryptedMocked".getBytes());
        ByteString decrypted = FarmerRegisteredEventFixture.johnDoe().getContactInfo().toByteString();

        FarmerRegisteredEvent.Builder encryptedEvent = FarmerRegisteredEvent.newBuilder()
                .setUuid(uuid)
                .setEncryptedPersonalData(EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder()
                        .setSubjectId(uuid)
                        .setData(encrypted)
                        .setPersonalDataFieldNumber(2))
                .setRegisteredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()));

        OneOfPersonalDataFieldDefinition personalDataFieldDef = FarmerRegisteredEventFixture.personalDataFieldDefinition();

        Decryptor decryptor = (key, data) -> decrypted.toByteArray();

        //when
        personalDataFieldDef.swapToDecrypted(decryptor, encryptedEvent);

        //then
        FarmerRegisteredEvent actual = encryptedEvent.build();

        assertThat(actual.getUuid())
                .isEqualTo(uuid);

        assertThat(actual.getEncryptedPersonalData())
            .isEqualTo(EncryptedPersonalDataOuterClass.EncryptedPersonalData.getDefaultInstance());

        assertThat(actual.getContactInfo())
                .isEqualTo(FarmerRegisteredEventFixture.johnDoe().getContactInfo());
    }


}