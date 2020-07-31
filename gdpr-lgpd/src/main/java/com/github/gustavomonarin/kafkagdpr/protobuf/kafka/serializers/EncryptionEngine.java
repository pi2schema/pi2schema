package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadata;
import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class EncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();

    public T encrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.encryptPersonalData(encryptingBuilder);

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }
}
