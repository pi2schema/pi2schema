package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject.SiblingSubjectIdentifierFinder;
import com.github.gustavomonarin.kafkagdpr.serializers.protobuf.subject.SubjectIdentifierFinder;
import com.google.protobuf.Descriptors.Descriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

//TODO: find better not null annotation and testing(findbugs 305?) in the build process ( with internal alias?)

public class PersonalMetadataProvider {

    private final SubjectIdentifierFinder subjectIdentifierFinder = new SiblingSubjectIdentifierFinder();

    public PersonalMetadata forDescriptor(@NotNull Descriptor descriptorForType) {
        //protobuf oneOf strategy
        List<OneOfPersonalDataFieldDefinition> encryptableFields = descriptorForType.getOneofs()
                .stream()
                .filter(OneOfPersonalDataFieldDefinition::hasPersonalData)
                .map(oneOfField ->
                        new OneOfPersonalDataFieldDefinition(
                                oneOfField,
                                subjectIdentifierFinder.find(oneOfField))
                )
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new PersonalMetadata(encryptableFields);
    }

}
