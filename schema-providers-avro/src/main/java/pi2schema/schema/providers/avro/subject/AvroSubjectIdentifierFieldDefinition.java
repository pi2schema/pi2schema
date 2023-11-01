package pi2schema.schema.providers.avro.subject;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;

public class AvroSubjectIdentifierFieldDefinition
        implements SubjectIdentifierFieldDefinition<SpecificRecordBase> {

    public static final String SUBJECT_IDENTIFIER_PROPERTY_NAME = "pi2schema-subject-identifier";

    private final Schema.Field subjectIdentifierField;

    public AvroSubjectIdentifierFieldDefinition(Schema.Field subjectIdentifierField) {
        this.subjectIdentifierField = subjectIdentifierField;
    }

    @Override
    public String subjectFrom(SpecificRecordBase buildingInstance) {
        return String.valueOf(buildingInstance.get(subjectIdentifierField.name()));
    }
}
