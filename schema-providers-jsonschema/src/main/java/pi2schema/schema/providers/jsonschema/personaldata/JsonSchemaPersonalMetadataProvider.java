package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.schema.providers.jsonschema.schema.JsonPiiFieldInfo;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of PersonalMetadataProvider for JSON Schema.
 * Provides PII metadata for business objects based on schema analysis.
 * Now directly handles schema analysis and caching.
 */
public class JsonSchemaPersonalMetadataProvider<T> implements PersonalMetadataProvider<T> {

    public static final String SUBJECT_IDENTIFIER_EXTENSION = "pi2schema-subject-identifier";
    public static final String PERSONAL_DATA_EXTENSION = "pi2schema-personal-data";

    private final Map<String, JsonPersonalMetadata<T>> metadataCache;
    private final ObjectMapper objectMapper;

    public JsonSchemaPersonalMetadataProvider() {
        this.metadataCache = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PersonalMetadata<T> forType(T originalObject) {
        throw new UnsupportedOperationException(
            "forType() is not supported for JSON Schema provider. Use forSchema() instead."
        );
    }

    /**
     * Creates PersonalMetadata for a given JSON Schema string.
     * This is the primary method for JSON Schema provider.
     */
    public JsonPersonalMetadata<T> forSchema(String schemaContent) {
        return metadataCache.computeIfAbsent(schemaContent, this::createMetadata);
    }

    /**
     * Creates PersonalMetadata for a given JSON Schema node.
     */
    public JsonPersonalMetadata<T> forSchema(JsonNode schemaNode) {
        String schemaContent = schemaNode.toString();
        return metadataCache.computeIfAbsent(schemaContent, content -> createMetadata(schemaNode));
    }

    /**
     * Creates PersonalMetadata using analyzed schema metadata.
     */
    public JsonPersonalMetadata<T> forSchemaMetadata(JsonSchemaMetadata schemaMetadata) {
        List<JsonPersonalDataFieldDefinition<T>> personalDataFieldDefinitions = schemaMetadata
            .getPiiFields()
            .stream()
            .map(piiFieldInfo -> new JsonPersonalDataFieldDefinition<T>(piiFieldInfo, schemaMetadata, objectMapper))
            .collect(Collectors.toList());

        return new JsonPersonalMetadata<>(personalDataFieldDefinitions);
    }

    private JsonPersonalMetadata<T> createMetadata(String schemaContent) {
        JsonSchemaMetadata schemaMetadata = analyzeSchema(schemaContent);
        return forSchemaMetadata(schemaMetadata);
    }

    private JsonPersonalMetadata<T> createMetadata(JsonNode schemaNode) {
        JsonSchemaMetadata schemaMetadata = analyzeSchema(schemaNode);
        return forSchemaMetadata(schemaMetadata);
    }

    // --- Merged analysis logic from JsonSchemaAnalyzer ---
    private JsonSchemaMetadata analyzeSchema(String schemaContent) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            return analyzeSchema(schemaNode);
        } catch (Exception e) {
            throw new JsonSchemaAnalysisException("Failed to parse JSON Schema", e);
        }
    }

    private JsonSchemaMetadata analyzeSchema(JsonNode schemaNode) {
        try {
            List<String> subjectIdentifierFields = new ArrayList<>();
            List<JsonPiiFieldInfo> piiFields = new ArrayList<>();
            analyzeProperties(schemaNode, "", subjectIdentifierFields, piiFields);
            return new JsonSchemaMetadata(schemaNode, subjectIdentifierFields, piiFields);
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
                    analyzeProperties(fieldSchema, fieldPath, subjectIdentifierFields, piiFields);
                });
        }
    }

    private void analyzeField(
        String fieldPath,
        JsonNode fieldSchema,
        List<String> subjectIdentifierFields,
        List<JsonPiiFieldInfo> piiFields
    ) {
        if (
            fieldSchema.has(SUBJECT_IDENTIFIER_EXTENSION) && fieldSchema.get(SUBJECT_IDENTIFIER_EXTENSION).asBoolean()
        ) {
            subjectIdentifierFields.add(fieldPath);
        }
        if (fieldSchema.has(PERSONAL_DATA_EXTENSION) && fieldSchema.get(PERSONAL_DATA_EXTENSION).asBoolean()) {
            piiFields.add(new JsonPiiFieldInfo(fieldPath, fieldSchema));
        }
    }

    public static class JsonSchemaAnalysisException extends RuntimeException {

        public JsonSchemaAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
