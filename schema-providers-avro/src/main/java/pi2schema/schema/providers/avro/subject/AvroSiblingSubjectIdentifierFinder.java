package pi2schema.schema.providers.avro.subject;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.schema.providers.avro.personaldata.AvroPersonalDataFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierFinder;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static pi2schema.schema.providers.avro.subject.AvroSubjectIdentifierFieldDefinition.SUBJECT_IDENTIFIER_PROPERTY_NAME;

public class AvroSiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<AvroPersonalDataFieldDefinition> {

    @Override
    public SubjectIdentifierFieldDefinition<SpecificRecordBase> find(AvroPersonalDataFieldDefinition fieldDescriptor) {
        var subjectsIdentifierFields = fieldDescriptor.getParentSchema().getFields().stream()
                .filter(AvroSiblingSubjectIdentifierFinder::isSubjectIdentifierField)
                .collect(toList());

        if (subjectsIdentifierFields.isEmpty()) { //This should return only optional empty in order to allow chained strategies
            throw new SubjectIdentifierNotFoundException(AvroSiblingSubjectIdentifierFinder.class, fieldDescriptor.getPersonalField().name());
        } else if (subjectsIdentifierFields.size() > 1) {
            throw new TooManySubjectIdentifiersException(
                    AvroSiblingSubjectIdentifierFinder.class,
                    fieldDescriptor.getPersonalField().name(),
                    subjectsIdentifierFields.size());
        }

        return new AvroSubjectIdentifierFieldDefinition(subjectsIdentifierFields.get(0));
    }

    private static boolean isSubjectIdentifierField(Schema.Field f) {
        return Optional.ofNullable(f.getObjectProp(SUBJECT_IDENTIFIER_PROPERTY_NAME))
                .map(Object::toString)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }
}
