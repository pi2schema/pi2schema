package pi2schema.schema.providers.protobuf.subject;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;

public class ProtobufSubjectIdentifierFieldDefinition implements SubjectIdentifierFieldDefinition<Message.Builder> {

    private final Descriptors.FieldDescriptor fieldDescriptor;

    public ProtobufSubjectIdentifierFieldDefinition(Descriptors.FieldDescriptor fieldDescriptor) {
        this.fieldDescriptor = fieldDescriptor;
    }

    @Override
    public String subjectFrom(Message.Builder buildingInstance) {
        return String.valueOf(buildingInstance.getField(fieldDescriptor));
    }
}
