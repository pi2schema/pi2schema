package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import com.google.protobuf.Descriptors;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

//TODO: find better not null annotation and testing(findbugs 305?) in the build process ( with internal alias?)

public class PersonalMetadataProvider {

    public PersonalMetadata forDescriptor(@NotNull Descriptors.Descriptor descriptorForType) {
        List<OneOfEncryptableField> encryptableFields = descriptorForType.getOneofs()
                .stream()
                .filter(OneOfEncryptableField::isEncryptable)
                .map(OneOfEncryptableField::new)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new PersonalMetadata(encryptableFields);
    }

}
