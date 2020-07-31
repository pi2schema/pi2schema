package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.EncryptionTargetFieldNotFoundException;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.PersonalDataFieldDefinition;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.TooManyEncryptionTargetFieldsException;
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

    static boolean hasPersonalData(@NotNull OneofDescriptor descriptor) {
        return descriptor.getFields()
                .stream()
                .anyMatch(isEncryptedFieldType);
    }

    @Override
    public void swapToEncrypted(Message.Builder buildingInstance) {
        Descriptors.FieldDescriptor unencryptedField = buildingInstance.getOneofFieldDescriptor(containerOneOfDescriptor);

        Message toBeEncrypted = (Message) buildingInstance.getField(unencryptedField);

        buildingInstance.clearOneof(containerOneOfDescriptor);
        buildingInstance.setField(targetFieldForEncryption, crypted(buildingInstance));

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

    @NotNull
    private EncryptedPersonalDataOuterClass.EncryptedPersonalData crypted(Message.Builder encryptingBuilder) {
        return EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder()
                .setSubjectId(subjectIdentifierFieldDefinition.actualValueFrom(encryptingBuilder))
                .build();
    }
}
