package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

public class SubjectIdentifierFieldDefinition {

    private final Descriptors.FieldDescriptor fieldDescriptor;

    public SubjectIdentifierFieldDefinition(Descriptors.FieldDescriptor fieldDescriptor) {
        this.fieldDescriptor = fieldDescriptor;
    }

    public String actualValueFrom(Message.Builder encryptingBuilder) {
        return String.valueOf(encryptingBuilder.getField(fieldDescriptor));
    }
}
