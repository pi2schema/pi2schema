package pi2schema.schema.providers.jsonschema.subject;

import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;

import java.util.Map;

/**
 * Implementation of SubjectIdentifierFieldDefinition for JSON Schema.
 * Extracts subject identifier values from business objects.
 */
public class JsonSubjectIdentifierFieldDefinition<T> implements SubjectIdentifierFieldDefinition<T> {

    public static final String SUBJECT_IDENTIFIER_EXTENSION = "pi2schema-subject-identifier";

    private final String fieldPath;
    private final ObjectMapper objectMapper;

    public JsonSubjectIdentifierFieldDefinition(String fieldPath) {
        this.fieldPath = fieldPath;
        this.objectMapper = new ObjectMapper();
    }

    public JsonSubjectIdentifierFieldDefinition(String fieldPath, ObjectMapper objectMapper) {
        this.fieldPath = fieldPath;
        this.objectMapper = objectMapper;
    }

    @Override
    public String subjectFrom(T instance) {
        // Convert business object to Map for field access
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = objectMapper.convertValue(instance, Map.class);

        Object value = getNestedValue(objectMap, fieldPath);
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
