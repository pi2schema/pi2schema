package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.PersonalMetadata;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class EncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();

    public T encrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.getEncryptableFields()
                .forEach(field -> field.swapToEncrypted(encryptingBuilder));

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }
}
