package pi2schema.schema.providers.protobuf.personaldata;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.schema.providers.protobuf.subject.SiblingSubjectIdentifierFinder;

import java.util.Collections;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class ProtobufPersonalMetadataProvider<T extends Message> implements PersonalMetadataProvider<T, Descriptor> {

    private final SiblingSubjectIdentifierFinder subjectIdentifierFinder = new SiblingSubjectIdentifierFinder();

    @Override
    public PersonalMetadata<T> forSchema(Descriptor schema) {
        //protobuf oneOf strategy
        var personalDataFieldDefinitions = schema
            .getOneofs()
            .stream()
            .filter(OneOfPersonalDataFieldDefinition::hasPersonalData)
            .map(this::createFieldDefinition)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new ProtobufPersonalMetadata<>(personalDataFieldDefinitions);
    }

    private OneOfPersonalDataFieldDefinition createFieldDefinition(Descriptors.OneofDescriptor descriptor) {
        return new OneOfPersonalDataFieldDefinition(descriptor, subjectIdentifierFinder.find(descriptor));
    }
}
