package pi2schema.schema.providers.jsonschema.subject;

import pi2schema.schema.providers.jsonschema.personaldata.JsonPersonalDataFieldDefinition;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierFinder;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import java.util.List;
import java.util.Map;

/**
 * Finds subject identifier fields in JSON Schema metadata.
 * Follows the sibling pattern used in Avro implementation.
 */
public class JsonSiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<JsonPersonalDataFieldDefinition> {

    @Override
    public SubjectIdentifierFieldDefinition<Map<String, Object>> find(JsonPersonalDataFieldDefinition fieldDescriptor) {
        JsonSchemaMetadata metadata = fieldDescriptor.getSchemaMetadata();
        List<String> subjectIdentifierFields = metadata.getSubjectIdentifierFields();

        if (subjectIdentifierFields.isEmpty()) {
            throw new SubjectIdentifierNotFoundException(
                JsonSiblingSubjectIdentifierFinder.class,
                fieldDescriptor.getFieldPath()
            );
        } else if (subjectIdentifierFields.size() > 1) {
            throw new TooManySubjectIdentifiersException(
                JsonSiblingSubjectIdentifierFinder.class,
                fieldDescriptor.getFieldPath(),
                subjectIdentifierFields.size()
            );
        }

        String subjectIdentifierField = subjectIdentifierFields.get(0);
        return new JsonSubjectIdentifierFieldDefinition(subjectIdentifierField);
    }
}
