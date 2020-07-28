package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject.SubjectIdentifierFieldDefinition;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class OneOfEncryptableField {

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = (f) ->
            EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final Descriptors.OneofDescriptor containerOneOfDescriptor;
    private final SubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition;

    public OneOfEncryptableField(Descriptors.OneofDescriptor descriptor,
                                 SubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition) {
        this.containerOneOfDescriptor = descriptor;
        this.subjectIdentifierFieldDefinition = subjectIdentifierFieldDefinition;
    }

    static boolean isEncryptable(@NotNull Descriptors.OneofDescriptor descriptor) {
        //TODO this validation should be on metadata generation
        return descriptor.getFields()
                .stream()
                .anyMatch(isEncryptedFieldType);
    }

    private FieldDescriptor targetField() {
        Stream<FieldDescriptor> encryptedFieldTypes = containerOneOfDescriptor.getFields()
                .stream()
                .filter(isEncryptedFieldType);


        //TODO on metadageneration, save target, which is immutable for the same metadata, no need to inspect again
        return encryptedFieldTypes.findFirst().orElseThrow(RuntimeException::new);
    }

    public void swapToEncrypted(Message.Builder encryptingBuilder) {
        Descriptors.FieldDescriptor unencryptedField = encryptingBuilder.getOneofFieldDescriptor(containerOneOfDescriptor);

        Message toBeEncrypted = (Message) encryptingBuilder.getField(unencryptedField);

        encryptingBuilder.clearOneof(containerOneOfDescriptor);
        encryptingBuilder.setField(targetField(), crypted(encryptingBuilder));

    }

    @NotNull
    private EncryptedPersonalDataOuterClass.EncryptedPersonalData crypted(Message.Builder encryptingBuilder) {
        return EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder()
                .setSubjectId(subjectIdentifierFieldDefinition.actualValueFrom(encryptingBuilder))
                .build();
    }
}
