package com.github.gustavomonarin.kafkagdpr.protobuf.subject;

import com.github.gustavomonarin.kafkagdpr.core.subject.SubjectIdentifierFieldDefinition;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

public class ProtobufSubjectIdentifierFieldDefinition
        implements SubjectIdentifierFieldDefinition<Message.Builder> {

    private final Descriptors.FieldDescriptor fieldDescriptor;

    public ProtobufSubjectIdentifierFieldDefinition(Descriptors.FieldDescriptor fieldDescriptor) {
        this.fieldDescriptor = fieldDescriptor;
    }

    @Override
    public String actualValueFrom(Message.Builder buildingInstance) {
        return String.valueOf(buildingInstance.getField(fieldDescriptor));
    }
}
