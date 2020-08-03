package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.*;
import com.github.gustavomonarin.kafkagdpr.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OneOfPersonalDataFieldDefinition
        implements PersonalDataFieldDefinition<Message.Builder> {

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = (f) ->
            EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final OneofDescriptor containerOneOfDescriptor;
    private final ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition;
    private final FieldDescriptor targetFieldForEncryption;

    public OneOfPersonalDataFieldDefinition(@NotNull OneofDescriptor descriptor,
                                            @NotNull ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition) {
        this.containerOneOfDescriptor = descriptor;
        this.subjectIdentifierFieldDefinition = subjectIdentifierFieldDefinition;
        this.targetFieldForEncryption = determineEncryptionField();
    }

    @Override
    public byte[] valueFrom(Message.Builder actualInstance) {
        Descriptors.FieldDescriptor unencryptedField = actualInstance.getOneofFieldDescriptor(containerOneOfDescriptor);
        Object value = actualInstance.getField(unencryptedField);

        if(value instanceof Message){
            return ((Message)value).toByteArray();
        }

        throw new UnsupportedPersonalDataFieldFormatException(unencryptedField.getFullName());
    }

    @Override
    public void swapToEncrypted(PersonalDataEncryptor encryptor,
                                Message.Builder buildingInstance) {

        EncryptedPersonalData encrypt = encryptor.encrypt(subjectIdentifierFieldDefinition.subjectFrom(buildingInstance),
                valueFrom(buildingInstance));

        buildingInstance.clearOneof(containerOneOfDescriptor);
        buildingInstance.setField(targetFieldForEncryption, encrypt);
    }

    static boolean hasPersonalData(@NotNull OneofDescriptor descriptor) {
        return descriptor.getFields()
                .stream()
                .anyMatch(isEncryptedFieldType);
    }

    private FieldDescriptor determineEncryptionField() {
        List<FieldDescriptor> encryptionFields = containerOneOfDescriptor.getFields()
                .stream()
                .filter(isEncryptedFieldType)
                .collect(Collectors.toList());

        if (encryptionFields.isEmpty()) {
            throw new EncryptionTargetFieldNotFoundException(containerOneOfDescriptor.getFullName());
        }

        if (encryptionFields.size() > 1) {
            throw new TooManyEncryptionTargetFieldsException(containerOneOfDescriptor.getFullName(), encryptionFields.size());
        }

        return encryptionFields.get(0);
    }


}
