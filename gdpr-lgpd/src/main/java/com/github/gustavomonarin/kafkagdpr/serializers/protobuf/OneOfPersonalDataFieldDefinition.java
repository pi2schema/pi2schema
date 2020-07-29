package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject.SubjectIdentifierFieldDefinition;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OneOfPersonalDataFieldDefinition {

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = (f) ->
            EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final OneofDescriptor containerOneOfDescriptor;
    private final SubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition;
    private final FieldDescriptor targetFieldForEncryption;

    public OneOfPersonalDataFieldDefinition(@NotNull OneofDescriptor descriptor,
                                            @NotNull SubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition) {
        this.containerOneOfDescriptor = descriptor;
        this.subjectIdentifierFieldDefinition = subjectIdentifierFieldDefinition;
        this.targetFieldForEncryption = determineEncryptionField();
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

        if(encryptionFields.size() > 1){
            throw new TooManyEncryptionTargetFieldsException(containerOneOfDescriptor.getFullName(), encryptionFields.size());
        }

        return encryptionFields.get(0);
    }

    public void swapToEncrypted(Message.Builder encryptingBuilder) {
        Descriptors.FieldDescriptor unencryptedField = encryptingBuilder.getOneofFieldDescriptor(containerOneOfDescriptor);

        Message toBeEncrypted = (Message) encryptingBuilder.getField(unencryptedField);

        encryptingBuilder.clearOneof(containerOneOfDescriptor);
        encryptingBuilder.setField(targetFieldForEncryption, crypted(encryptingBuilder));

    }

    @NotNull
    private EncryptedPersonalDataOuterClass.EncryptedPersonalData crypted(Message.Builder encryptingBuilder) {
        return EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder()
                .setSubjectId(subjectIdentifierFieldDefinition.actualValueFrom(encryptingBuilder))
                .build();
    }
}
