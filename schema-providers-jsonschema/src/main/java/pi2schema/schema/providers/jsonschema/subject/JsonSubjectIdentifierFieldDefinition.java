package pi2schema.schema.providers.jsonschema.subject;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.beanutils.PropertyUtils;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierRetrievalException;

/**
 * Implementation of SubjectIdentifierFieldDefinition for JSON Schema.
 * Extracts subject identifier values from business objects.
 */
public class JsonSubjectIdentifierFieldDefinition implements SubjectIdentifierFieldDefinition<Object> {

    public static final String SUBJECT_IDENTIFIER_EXTENSION = "pi2schema-subject-identifier";

    private final String identifierFieldPath;

    JsonSubjectIdentifierFieldDefinition(String identifierFieldPath) {
        this.identifierFieldPath = identifierFieldPath;
    }

    @Override
    public String subjectFrom(Object instance) {
        try {
            Object propertyValue = PropertyUtils.getProperty(instance, identifierFieldPath);
            if (propertyValue == null) {
                throw new SubjectIdentifierRetrievalException(
                    instance,
                    identifierFieldPath,
                    new IllegalStateException("Subject identifier field '" + identifierFieldPath + "' is null")
                );
            }
            return propertyValue.toString();
        } catch (Exception e) {
            throw new SubjectIdentifierRetrievalException(instance, identifierFieldPath, e);
        }
    }

    static boolean isSubjectIdentifier(JsonNode field) {
        return (
            field.has(SUBJECT_IDENTIFIER_EXTENSION) &&
            field.get(SUBJECT_IDENTIFIER_EXTENSION).isBoolean() &&
            field.get(SUBJECT_IDENTIFIER_EXTENSION).asBoolean()
        );
    }
}
