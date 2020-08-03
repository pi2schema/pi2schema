package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PersonalMetadata {

    private final List<OneOfPersonalDataFieldDefinition> personalDataFields;

    public PersonalMetadata(@NotNull List<OneOfPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    public void encryptPersonalData(ProtobufPersonalDataEncryptor encryptor, Message.Builder encryptingBuilder) {

        personalDataFields.forEach(field ->
                field.swapToEncrypted(encryptor, encryptingBuilder)
        );
    }
}
