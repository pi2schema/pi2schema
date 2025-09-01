package pi2schema.schema.providers.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import pi2schema.schema.SchemaProvider;

/**
 * JSON Schema-specific provider interface.
 * Extends the generic SchemaProvider with JSON Schema specifics.
 */
public interface JsonSchemaProvider extends SchemaProvider<JsonNode> {
    /**
     * Discovers the JSON Schema for a given business object using optional context information.
     *
     * @param businessObject The object to find schema for
     * @param context Optional context that provides additional information for schema discovery
     * @return The JsonNode representing the schema
     * @throws pi2schema.schema.SchemaNotFoundException if no schema can be found for the object
     */
    @Override
    JsonNode schemaFor(Object businessObject, Object context);

    /**
     * Discovers the JSON Schema for a given business object without additional context.
     *
     * @param businessObject The object to find schema for
     * @return The JsonNode representing the schema
     * @throws pi2schema.schema.SchemaNotFoundException if no schema can be found for the object
     */
    @Override
    default JsonNode schemaFor(Object businessObject) {
        return schemaFor(businessObject, null);
    }
}
