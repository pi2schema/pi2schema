package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.JsonNode;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;
import pi2schema.schema.providers.jsonschema.json.JsonField;
import pi2schema.schema.providers.jsonschema.subject.JsonSiblingSubjectIdentifierFinder;
import pi2schema.schema.providers.jsonschema.subject.JsonSubjectIdentifierFieldDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static pi2schema.schema.providers.jsonschema.personaldata.JsonPersonalDataFieldDefinition.hasPersonalData;

/**
 * Implementation of PersonalMetadataProvider for JSON Schema.
 * Provides PII metadata for business objects based on schema analysis.
 * Simplified to directly handle schema analysis and field definition creation.
 */
public class JsonSchemaPersonalMetadataProvider<T> implements PersonalMetadataProvider<T, JsonNode> {

    private final Map<String, JsonPersonalMetadata<T>> metadataCache;
    private final JsonSiblingSubjectIdentifierFinder subjectIdentifierFinder = new JsonSiblingSubjectIdentifierFinder();

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

        // TODO, handle better json schema support including references
        JsonNode unwrappedFromSchemaEnvelop = schema.get("properties");

        List<JsonPersonalDataFieldDefinition<T>> fields = unwrappedFromSchemaEnvelop
            .properties()
            .stream()
            .filter(e -> hasPersonalData(e.getValue()))
            .map(e -> new JsonField(e.getKey(), e.getValue(), unwrappedFromSchemaEnvelop))
            .map(this::createFieldDefinition)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new JsonPersonalMetadata<>(fields);
    }

    private JsonPersonalDataFieldDefinition<T> createFieldDefinition(JsonField field) {
        JsonSubjectIdentifierFieldDefinition jsonSubjectIdentifierFieldDefinition = subjectIdentifierFinder.find(field);
        return new JsonPersonalDataFieldDefinition<>(field.absolutPath(), jsonSubjectIdentifierFieldDefinition);
    }
}
