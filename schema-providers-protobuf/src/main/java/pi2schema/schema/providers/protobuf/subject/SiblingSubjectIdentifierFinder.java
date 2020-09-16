package pi2schema.schema.providers.protobuf.subject;

import pi2schema.Subject;
import pi2schema.schema.subject.SubjectIdentifierFinder;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import java.util.List;

import static com.google.protobuf.Descriptors.FieldDescriptor;
import static com.google.protobuf.Descriptors.OneofDescriptor;
import static java.util.stream.Collectors.toList;

public class SiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<OneofDescriptor> {

    @Override
    public ProtobufSubjectIdentifierFieldDefinition find(OneofDescriptor fieldDescriptor) {
        List<FieldDescriptor> subjectsFields = fieldDescriptor.getContainingType() //parent
                .getFields() //siblings
                .stream()
                .filter(f -> f.getOptions().getExtension(Subject.subjectIdentifier))
                .collect(toList());


        if (subjectsFields.isEmpty()) { //This should return only optional empty in order to allow chained strategies
            throw new SubjectIdentifierNotFoundException(SiblingSubjectIdentifierFinder.class, fieldDescriptor.getFullName());
        } else if (subjectsFields.size() > 1) {
            throw new TooManySubjectIdentifiersException(
                    SiblingSubjectIdentifierFinder.class,
                    fieldDescriptor.getFullName(),
                    subjectsFields.size());
        }

        return new ProtobufSubjectIdentifierFieldDefinition(subjectsFields.get(0));
    }

}
