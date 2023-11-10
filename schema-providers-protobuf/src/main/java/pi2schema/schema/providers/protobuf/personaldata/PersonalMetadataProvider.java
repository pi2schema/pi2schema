package pi2schema.schema.providers.protobuf.personaldata;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import pi2schema.schema.providers.protobuf.subject.SiblingSubjectIdentifierFinder;

import java.util.Collections;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class PersonalMetadataProvider {

    private final SiblingSubjectIdentifierFinder subjectIdentifierFinder = new SiblingSubjectIdentifierFinder();

    public PersonalMetadata forDescriptor(Descriptor descriptorForType) {
        //protobuf oneOf strategy
        var personalDataFieldDefinitions = descriptorForType
            .getOneofs()
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
