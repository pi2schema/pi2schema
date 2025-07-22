package pi2schema.schema.providers.jsonschema.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles JSON Schema parsing and analysis for PII field identification.
 * Provides caching for performance optimization as required by the specification.
 */
public class JsonSchemaAnalyzer {

    public static final String SUBJECT_IDENTIFIER_EXTENSION = "pi2schema-subject-identifier";
    public static final String PERSONAL_DATA_EXTENSION = "pi2schema-personal-data";

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchemaMetadata> schemaCache;

    public JsonSchemaAnalyzer() {
        this.objectMapper = new ObjectMapper();
        this.schemaCache = new ConcurrentHashMap<>();
    }

    /**
     * Analyzes a JSON Schema to extract PII and subject identifier field information.
     * Results are cached for performance.
     */
    public JsonSchemaMetadata analyzeSchema(String schemaContent) {
        return schemaCache.computeIfAbsent(schemaContent, this::doAnalyzeSchema);
    }

    /**
     * Analyzes a JSON Schema from an InputStream.
     */
    public JsonSchemaMetadata analyzeSchema(InputStream schemaStream) {
        try {
            String schemaContent = new String(schemaStream.readAllBytes());
            return analyzeSchema(schemaContent);
        } catch (Exception e) {
            throw new JsonSchemaAnalysisException("Failed to read schema from InputStream", e);
        }
    }

    private JsonSchemaMetadata doAnalyzeSchema(String schemaContent) {
        try {
            JSONObject jsonSchema = new JSONObject(new JSONTokener(schemaContent));
            Schema schema = SchemaLoader.load(jsonSchema);

            JsonNode schemaNode = objectMapper.readTree(schemaContent);

            List<String> subjectIdentifierFields = new ArrayList<>();
            List<JsonPiiFieldInfo> piiFields = new ArrayList<>();

            analyzeProperties(schemaNode, "", subjectIdentifierFields, piiFields);

            return new JsonSchemaMetadata(schema, schemaNode, subjectIdentifierFields, piiFields);
        } catch (Exception e) {
            throw new JsonSchemaAnalysisException("Failed to analyze JSON Schema", e);
        }
    }

    private void analyzeProperties(
        JsonNode node,
        String currentPath,
        List<String> subjectIdentifierFields,
        List<JsonPiiFieldInfo> piiFields
    ) {
        if (node == null || !node.isObject()) {
            return;
        }

        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            properties
                .fields()
                .forEachRemaining(entry -> {
                    String fieldName = entry.getKey();
                    JsonNode fieldSchema = entry.getValue();
                    String fieldPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

                    analyzeField(fieldPath, fieldSchema, subjectIdentifierFields, piiFields);

                    // Recursively analyze nested objects
                    analyzeProperties(fieldSchema, fieldPath, subjectIdentifierFields, piiFields);
                });
        }

        // Handle oneOf/anyOf patterns
        analyzeOneOfAnyOf(node, currentPath, subjectIdentifierFields, piiFields);
    }

    private void analyzeField(
        String fieldPath,
        JsonNode fieldSchema,
        List<String> subjectIdentifierFields,
        List<JsonPiiFieldInfo> piiFields
    ) {
        // Check for subject identifier extension
        if (
            fieldSchema.has(SUBJECT_IDENTIFIER_EXTENSION) && fieldSchema.get(SUBJECT_IDENTIFIER_EXTENSION).asBoolean()
        ) {
            subjectIdentifierFields.add(fieldPath);
        }

        // Check for personal data extension
        if (fieldSchema.has(PERSONAL_DATA_EXTENSION) && fieldSchema.get(PERSONAL_DATA_EXTENSION).asBoolean()) {
            piiFields.add(new JsonPiiFieldInfo(fieldPath, fieldSchema));
        }

        // Check oneOf/anyOf for PII fields
        if (fieldSchema.has("oneOf") || fieldSchema.has("anyOf")) {
            String arrayKey = fieldSchema.has("oneOf") ? "oneOf" : "anyOf";
            JsonNode alternatives = fieldSchema.get(arrayKey);

            boolean hasPiiAlternative = false;
            boolean hasEncryptedAlternative = false;

            if (alternatives.isArray()) {
                for (JsonNode alternative : alternatives) {
                    if (
                        alternative.has(PERSONAL_DATA_EXTENSION) && alternative.get(PERSONAL_DATA_EXTENSION).asBoolean()
                    ) {
                        hasPiiAlternative = true;
                    }

                    if (alternative.has("$ref") && alternative.get("$ref").asText().contains("EncryptedPersonalData")) {
                        hasEncryptedAlternative = true;
                    }
                }
            }

            if (hasPiiAlternative && hasEncryptedAlternative) {
                piiFields.add(new JsonPiiFieldInfo(fieldPath, fieldSchema));
            }
        }
    }

    private void analyzeOneOfAnyOf(
        JsonNode node,
        String currentPath,
        List<String> subjectIdentifierFields,
        List<JsonPiiFieldInfo> piiFields
    ) {
        for (String key : Arrays.asList("oneOf", "anyOf")) {
            JsonNode alternatives = node.get(key);
            if (alternatives != null && alternatives.isArray()) {
                for (JsonNode alternative : alternatives) {
                    analyzeProperties(alternative, currentPath, subjectIdentifierFields, piiFields);
                }
            }
        }
    }

    /**
     * Validates a JSON object against the analyzed schema.
     */
    public void validateJson(JsonSchemaMetadata metadata, JsonNode jsonObject) {
        try {
            String jsonString = objectMapper.writeValueAsString(jsonObject);
            JSONObject jsonObj = new JSONObject(jsonString);
            metadata.getSchema().validate(jsonObj);
        } catch (ValidationException e) {
            throw new JsonSchemaValidationException("JSON validation failed", e);
        } catch (Exception e) {
            throw new JsonSchemaAnalysisException("Failed to validate JSON", e);
        }
    }

    public static class JsonSchemaAnalysisException extends RuntimeException {

        public JsonSchemaAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class JsonSchemaValidationException extends RuntimeException {

        public JsonSchemaValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
