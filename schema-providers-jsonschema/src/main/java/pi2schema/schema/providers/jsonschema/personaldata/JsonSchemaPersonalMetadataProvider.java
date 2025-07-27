package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.JsonNode;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.schema.providers.jsonschema.json.JsonField;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static pi2schema.schema.providers.jsonschema.personaldata.JsonPersonalDataFieldDefinition.hasPersonalData;

/**
 * Implementation of PersonalMetadataProvider for JSON Schema.
 * Provides PII metadata for business objects based on schema analysis.
 * Simplified to directly handle schema analysis and field definition creation.
 */
public class JsonSchemaPersonalMetadataProvider<T> implements PersonalMetadataProvider<T> {

    private final Map<String, JsonPersonalMetadata<T>> metadataCache;

    public JsonSchemaPersonalMetadataProvider() {
        this.metadataCache = new ConcurrentHashMap<>();
    }

    @Override
    public PersonalMetadata<T> forType(T originalObject) {
        throw new UnsupportedOperationException(
            "forType() is not supported for JSON Schema provider. Use forSchema() instead."
        );
    }

    /**
     * Creates PersonalMetadata for a given JSON Schema node.
     * This is the primary method for JSON Schema provider.
     */
    public JsonPersonalMetadata<T> forSchema(JsonNode schema) {
        String schemaContent = schema.toString();
        return metadataCache.computeIfAbsent(schemaContent, content -> createMetadata(schema));
    }

    private JsonPersonalMetadata<T> createMetadata(JsonNode schema) {
        if (!schema.isObject()) {
            return new JsonPersonalMetadata<>(Collections.emptyList());
        }

        schema
            .properties()
            .stream()
            .filter(e -> hasPersonalData(e.getValue()))
            .map(e -> new JsonField(e.getKey(), e.getValue(), schema))
            .map(this::createFieldDefinition);

        return null;
    }

    private JsonPersonalDataFieldDefinition createFieldDefinition(JsonField node) {
        return null;
    }
}
