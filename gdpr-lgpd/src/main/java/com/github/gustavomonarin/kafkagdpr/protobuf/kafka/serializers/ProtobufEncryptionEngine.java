package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.github.gustavomonarin.kafkagdpr.core.encryption.Encryptor;
import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadata;
import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class ProtobufEncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Encryptor encryptor;

    public ProtobufEncryptionEngine(Encryptor encryptor) {
        this.encryptor = encryptor;
    }

    public T encrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.encryptPersonalData(encryptor, encryptingBuilder);

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }
}
