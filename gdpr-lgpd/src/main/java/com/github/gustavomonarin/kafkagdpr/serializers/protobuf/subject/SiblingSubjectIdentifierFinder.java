package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject;

import com.github.gustavomonarin.gdpr.Subject;
import com.google.protobuf.Descriptors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class SiblingSubjectIdentifierFinder implements SubjectIdentifierFinder {

    @Override
    public SubjectIdentifierFieldDefinition find(@NotNull Descriptors.OneofDescriptor fieldDescriptor) {
        List<Descriptors.FieldDescriptor> subjectsFields = fieldDescriptor.getContainingType() //parent
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

        return new SubjectIdentifierFieldDefinition(subjectsFields.get(0));
    }

}
