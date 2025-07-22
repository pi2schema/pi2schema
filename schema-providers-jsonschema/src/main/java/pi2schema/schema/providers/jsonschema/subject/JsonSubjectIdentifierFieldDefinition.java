package pi2schema.schema.providers.jsonschema.subject;

import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;

import java.util.Map;

/**
 * Implementation of SubjectIdentifierFieldDefinition for JSON Schema.
 * Extracts subject identifier values from JSON objects.
 */
public class JsonSubjectIdentifierFieldDefinition implements SubjectIdentifierFieldDefinition<Map<String, Object>> {

    public static final String SUBJECT_IDENTIFIER_EXTENSION = "pi2schema-subject-identifier";

    private final String fieldPath;

    public JsonSubjectIdentifierFieldDefinition(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    @Override
    public String subjectFrom(Map<String, Object> instance) {
        Object value = getNestedValue(instance, fieldPath);
        if (value == null) {
            throw new IllegalArgumentException("Subject identifier field '" + fieldPath + "' is null or missing");
        }
        return String.valueOf(value);
    }

    public String getFieldPath() {
        return fieldPath;
    }

    /**
     * Extracts a nested value from a map using dot notation (e.g., "user.profile.id").
     */
    private Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }

        String[] pathParts = path.split("\\.");
        Object current = map;

        for (String part : pathParts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }
}
