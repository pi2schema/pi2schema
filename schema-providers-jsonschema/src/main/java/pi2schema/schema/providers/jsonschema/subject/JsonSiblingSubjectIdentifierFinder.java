package pi2schema.schema.providers.jsonschema.subject;

import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import java.util.List;

/**
 * Finds subject identifier fields in JSON Schema metadata.
 * Follows the sibling pattern used in Avro implementation.
 */
public class JsonSiblingSubjectIdentifierFinder<T> {

    /**
     * Finds subject identifier field definition from schema metadata and field path.
     */
    public SubjectIdentifierFieldDefinition<T> find(JsonSchemaMetadata metadata, String fieldPath) {
        List<String> subjectIdentifierFields = metadata.getSubjectIdentifierFields();

        if (subjectIdentifierFields.isEmpty()) {
            throw new SubjectIdentifierNotFoundException(JsonSiblingSubjectIdentifierFinder.class, fieldPath);
        } else if (subjectIdentifierFields.size() > 1) {
            throw new TooManySubjectIdentifiersException(
                JsonSiblingSubjectIdentifierFinder.class,
                fieldPath,
                subjectIdentifierFields.size()
            );
        }

        String subjectIdentifierField = subjectIdentifierFields.get(0);
        return new JsonSubjectIdentifierFieldDefinition<>(subjectIdentifierField);
    }
}
