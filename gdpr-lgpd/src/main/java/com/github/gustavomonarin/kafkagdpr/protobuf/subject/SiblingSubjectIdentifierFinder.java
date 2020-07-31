package com.github.gustavomonarin.kafkagdpr.protobuf.subject;

import com.github.gustavomonarin.gdpr.Subject;
import com.github.gustavomonarin.kafkagdpr.core.subject.SubjectIdentifierFinder;
import com.github.gustavomonarin.kafkagdpr.core.subject.SubjectIdentifierNotFoundException;
import com.github.gustavomonarin.kafkagdpr.core.subject.TooManySubjectIdentifiersException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.protobuf.Descriptors.FieldDescriptor;
import static com.google.protobuf.Descriptors.OneofDescriptor;
import static java.util.stream.Collectors.toList;

public class SiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<OneofDescriptor> {

    @Override
    public ProtobufSubjectIdentifierFieldDefinition find(@NotNull OneofDescriptor fieldDescriptor) {
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
