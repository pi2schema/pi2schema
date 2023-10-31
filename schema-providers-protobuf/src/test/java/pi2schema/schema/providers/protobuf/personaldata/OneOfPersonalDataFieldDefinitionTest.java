//package pi2schema.schema.providers.protobuf.personaldata;
//
//import com.acme.FarmerRegisteredEventFixture;
//import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
//import com.acme.InvalidOneOfPersonalData;
//import com.google.protobuf.ByteString;
//import com.google.protobuf.Timestamp;
//import org.junit.jupiter.api.Test;
//import org.opentest4j.AssertionFailedError;
//import pi2schema.crypto.Decryptor;
//import pi2schema.crypto.Encryptor;
//import pi2schema.schema.personaldata.EncryptionTargetFieldNotFoundException;
//import pi2schema.schema.personaldata.TooManyEncryptionTargetFieldsException;
//import pi2schema.schema.providers.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;
//
//import java.time.Instant;
//import java.util.UUID;
//import java.util.concurrent.CompletableFuture;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
//import static pi2schema.EncryptedPersonalDataV1.EncryptedPersonalData;
//
//class OneOfPersonalDataFieldDefinitionTest {
//
//    @Test
//    void shouldThrowEncryptionTargetFieldNotFoundException() {
//
//        var descriptor = InvalidOneOfPersonalData.MissingEncryptedPersonalDataField.getDescriptor();
//        var personalDataField = descriptor.getOneofs().get(0);
//        var subjectField = new ProtobufSubjectIdentifierFieldDefinition(descriptor.getFields().get(0));
//
//        assertThatExceptionOfType(EncryptionTargetFieldNotFoundException.class)
//                .isThrownBy(() ->
//                        new OneOfPersonalDataFieldDefinition(personalDataField, subjectField)
//                )
//                .withMessage("The personal data container com.acme.MissingEncryptedPersonalDataField.personal_data " +
//                        "does not encapsulate a " + EncryptedPersonalData.getDescriptor().getFullName() + " while exact one is required");
//
//    }
//
//    @Test
//    void shouldThrowTooManyEncryptionTargetFieldException() {
//
//        var descriptor = InvalidOneOfPersonalData.MultipleEncryptedPersonalDataField.getDescriptor();
//        var personalDataField = descriptor.getOneofs().get(0);
//        var subjectField = new ProtobufSubjectIdentifierFieldDefinition(descriptor.getFields().get(0));
//
//        assertThatExceptionOfType(TooManyEncryptionTargetFieldsException.class)
//                .isThrownBy(() ->
//                        new OneOfPersonalDataFieldDefinition(personalDataField, subjectField)
//                )
//                .withMessage("The personal data container com.acme.MultipleEncryptedPersonalDataField.personal_data " +
//                        "has 2 fields of type " + EncryptedPersonalData.getDescriptor().getFullName() + " while exact one is required");
//    }
//
//    @Test
//    void targetField() {
//        var personalData = FarmerRegisteredEventFixture.personalDataFieldDefinition();
//
//        var encryptionTargetField = personalData.encryptionTargetField();
//
//        assertThat(encryptionTargetField.getFullName())
//                .isEqualTo("com.acme.FarmerRegisteredEvent.encryptedPersonalData");
//
//        assertThat(encryptionTargetField.getNumber())
//                .isEqualTo(3);
//    }
//
//    @Test
//    void personalDataField() {
//        var personalData = FarmerRegisteredEventFixture.personalDataFieldDefinition();
//
//        var peresonalDataTargetField = personalData.personalDataTargetField(2);
//
//        assertThat(peresonalDataTargetField.getFullName())
//                .isEqualTo("com.acme.FarmerRegisteredEvent.contact_info");
//    }
//
//    @Test
//    void swapToEncryptedShouldIgnoreEncryptionWhenPersonalDataIsNotSet() {
//
//        // given
//        var uuid = UUID.randomUUID().toString();
//        var event = FarmerRegisteredEvent.newBuilder().setUuid(uuid);
//
//        var personalDataFieldDef = FarmerRegisteredEventFixture.personalDataFieldDefinition();
//
//        Encryptor encryptor = (subject, data) -> {
//            throw new AssertionFailedError("Should not encrypt anything, as there is no personal data set");
//        };
//
//        var expected = event.clone(); //expected with no changes
//
////        when
//        personalDataFieldDef.swapToEncrypted(encryptor, event);
//
////        then
//        assertThat(event.build()).isEqualTo(expected.build());
//    }
//
//    @Test
//    void swapToEncrypted() {
//
//    }
//
//    @Test
//    void swapToDecryted() {
//        //given
//        var uuid = UUID.randomUUID().toString();
//        var encrypted = ByteString.copyFrom("encryptedMocked".getBytes());
//        var decrypted = FarmerRegisteredEventFixture.johnDoe().getContactInfo().toByteString();
//
//        var encryptedEvent = FarmerRegisteredEvent.newBuilder()
//                .setUuid(uuid)
//                .setEncryptedPersonalData(EncryptedPersonalData.newBuilder()
//                        .setSubjectId(uuid)
//                        .setData(encrypted)
//                        .setPersonalDataFieldNumber(2))
//                .setRegisteredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()));
//
//        var personalDataFieldDef = FarmerRegisteredEventFixture.personalDataFieldDefinition();
//
//        Decryptor decryptor = (key, data) ->
//                CompletableFuture.completedFuture(decrypted.asReadOnlyByteBuffer());
//
//        //when
//        personalDataFieldDef.swapToDecrypted(decryptor, encryptedEvent);
//
//        //then
//        var actual = encryptedEvent.build();
//
//        assertThat(actual.getUuid())
//                .isEqualTo(uuid);
//
//        assertThat(actual.getEncryptedPersonalData())
//                .isEqualTo(EncryptedPersonalData.getDefaultInstance());
//
//        assertThat(actual.getContactInfo())
//                .isEqualTo(FarmerRegisteredEventFixture.johnDoe().getContactInfo());
//    }
//}