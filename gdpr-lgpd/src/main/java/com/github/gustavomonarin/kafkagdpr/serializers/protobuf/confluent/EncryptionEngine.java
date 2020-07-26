package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.PersonalMetadata;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.PersonalMetadataProvider;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class EncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();

    public T encrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.getEncryptableFields().forEach(field -> {
                    Descriptors.FieldDescriptor currentSetFieldDescriptor = data.getOneofFieldDescriptor(field.getContainerOneOfDescriptor());

                    Object toBeEncrypted = data.getField(currentSetFieldDescriptor);

                    if (toBeEncrypted instanceof Message) {
                        Message messageToBeEncrypted = (Message) toBeEncrypted;

                        encryptingBuilder.clearOneof(field.getContainerOneOfDescriptor());
                        encryptingBuilder.setField(field.getEncryptedTargetField(), crypted());
                    }
                }
        );

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }

    @NotNull
    private EncryptedPersonalDataOuterClass.EncryptedPersonalData crypted() {
        return EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder().build();
    }
}
