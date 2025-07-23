package pi2schema.schema.providers.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import pi2schema.schema.SchemaProvider;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * JSON Schema-specific provider interface.
 * Extends the generic SchemaProvider with JSON Schema specifics.
 */
public interface JsonSchemaProvider extends SchemaProvider<JsonNode> {
    /**
     * Discovers the JSON Schema for a given business object using an optional schema ID.
     *
     * @param businessObject The object to find schema for
     * @param schemaIdSupplier Optional supplier that provides schema ID (e.g., from Kafka headers)
     * @return The JsonNode representing the schema
     * @throws pi2schema.schema.SchemaNotFoundException if no schema can be found for the object
     */
    @Override
    JsonNode schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier);

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
