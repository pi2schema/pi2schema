package pi2schema.schema.providers.jsonschema.schema;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents information about a PII field extracted from JSON Schema analysis.
 */
public class JsonPiiFieldInfo {

    private final String fieldPath;
    private final JsonNode fieldSchema;

    public JsonPiiFieldInfo(String fieldPath, JsonNode fieldSchema) {
        this.fieldPath = fieldPath;
        this.fieldSchema = fieldSchema;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public JsonNode getFieldSchema() {
        return fieldSchema;
    }

    /**
     * Gets the field name (last part of the path).
     */
    public String getFieldName() {
        int lastDotIndex = fieldPath.lastIndexOf('.');
        return lastDotIndex >= 0 ? fieldPath.substring(lastDotIndex + 1) : fieldPath;
    }

    /**
     * Checks if this is a nested field (contains dots in path).
     */
    public boolean isNested() {
        return fieldPath.contains(".");
    }

    @Override
    public String toString() {
        return "JsonPiiFieldInfo{" + "fieldPath='" + fieldPath + '\'' + '}';
    }
}
