package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.everit.json.schema.Schema;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.schema.providers.jsonschema.JsonSchemaProvider;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaAnalyzer;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of PersonalMetadataProvider for JSON Schema.
 * Provides PII metadata for JSON objects based on schema analysis.
 */
public class JsonSchemaPersonalMetadataProvider implements PersonalMetadataProvider<Map<String, Object>> {

    private final JsonSchemaAnalyzer schemaAnalyzer;
    private final Map<String, JsonPersonalMetadata> metadataCache;
    private final JsonSchemaProvider schemaProvider;
    private final ObjectMapper objectMapper;

    public JsonSchemaPersonalMetadataProvider() {
        this.schemaAnalyzer = new JsonSchemaAnalyzer();
        this.metadataCache = new ConcurrentHashMap<>();
        this.schemaProvider = null; // Will throw UnsupportedOperationException for forType()
        this.objectMapper = new ObjectMapper();
    }

    public JsonSchemaPersonalMetadataProvider(JsonSchemaProvider schemaProvider) {
        this.schemaAnalyzer = new JsonSchemaAnalyzer();
        this.metadataCache = new ConcurrentHashMap<>();
        this.schemaProvider = schemaProvider;
        this.objectMapper = new ObjectMapper();
    }

    public JsonSchemaPersonalMetadataProvider(JsonSchemaAnalyzer schemaAnalyzer) {
        this.schemaAnalyzer = schemaAnalyzer;
        this.metadataCache = new ConcurrentHashMap<>();
        this.schemaProvider = null; // Will throw UnsupportedOperationException for forType()
        this.objectMapper = new ObjectMapper();
    }

    public JsonSchemaPersonalMetadataProvider(JsonSchemaProvider schemaProvider, JsonSchemaAnalyzer schemaAnalyzer) {
        this.schemaAnalyzer = schemaAnalyzer;
        this.metadataCache = new ConcurrentHashMap<>();
        this.schemaProvider = schemaProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PersonalMetadata<Map<String, Object>> forType(Map<String, Object> originalObject) {
        if (schemaProvider == null) {
            throw new UnsupportedOperationException(
                "JSON Schema provider requires explicit schema. Use forSchema(String) method or configure a JsonSchemaProvider."
            );
        }

        // Convert Map to business object for schema discovery
        Object businessObject = convertMapToBusinessObject(originalObject);
        Schema schema = schemaProvider.schemaFor(businessObject);
        return forSchema(schema.toString());
    }

    /**
     * Creates PersonalMetadata for a given JSON Schema.
     * This is the primary method for JSON Schema provider.
     */
    public JsonPersonalMetadata forSchema(String schemaContent) {
        return metadataCache.computeIfAbsent(schemaContent, this::createMetadata);
    }

    /**
     * Creates PersonalMetadata using analyzed schema metadata.
     */
    public JsonPersonalMetadata forSchemaMetadata(JsonSchemaMetadata schemaMetadata) {
        List<JsonPersonalDataFieldDefinition> personalDataFieldDefinitions = schemaMetadata
            .getPiiFields()
            .stream()
            .map(piiFieldInfo -> new JsonPersonalDataFieldDefinition(piiFieldInfo, schemaMetadata))
            .collect(Collectors.toList());

        return new JsonPersonalMetadata(personalDataFieldDefinitions);
    }

    private JsonPersonalMetadata createMetadata(String schemaContent) {
        JsonSchemaMetadata schemaMetadata = schemaAnalyzer.analyzeSchema(schemaContent);
        return forSchemaMetadata(schemaMetadata);
    }

    /**
     * Analyzes a JSON Schema and returns the metadata.
     */
    public JsonSchemaMetadata analyzeSchema(String schemaContent) {
        return schemaAnalyzer.analyzeSchema(schemaContent);
    }

    /**
     * Clears the metadata cache.
     */
    public void clearCache() {
        metadataCache.clear();
    }

    /**
     * Converts a Map<String, Object> to a business object for schema discovery.
     * For now, this is a simple pass-through, but could be enhanced to convert
     * to actual business object types if needed.
     */
    private Object convertMapToBusinessObject(Map<String, Object> originalObject) {
        // For JSON Schema, the Map itself can serve as the business object
        // since JsonSchemaProvider implementations can work with generic objects
        return originalObject;
    }
}
