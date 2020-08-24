package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;

import com.github.gustavomonarin.kafkagdpr.protobuf.subject.SiblingSubjectIdentifierFinder;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class PersonalMetadataProvider {

    private final SiblingSubjectIdentifierFinder subjectIdentifierFinder = new SiblingSubjectIdentifierFinder();

    public PersonalMetadata forDescriptor(@NotNull Descriptor descriptorForType) {

        //protobuf oneOf strategy
        List<OneOfPersonalDataFieldDefinition> personalDataFieldDefinitions = descriptorForType.getOneofs()
                .stream()
                .filter(OneOfPersonalDataFieldDefinition::hasPersonalData)
                .map(this::createFieldDefinition)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new PersonalMetadata(personalDataFieldDefinitions);
    }

    private OneOfPersonalDataFieldDefinition createFieldDefinition(Descriptors.OneofDescriptor descriptor) {
        return new OneOfPersonalDataFieldDefinition(descriptor, subjectIdentifierFinder.find(descriptor));
    }
}
