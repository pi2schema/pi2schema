package pi2schema.schema.providers.jsonschema;

import org.everit.json.schema.Schema;
import pi2schema.schema.SchemaProvider;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * JSON Schema-specific provider interface.
 * Extends the generic SchemaProvider with JSON Schema specifics.
 */
public interface JsonSchemaProvider extends SchemaProvider<Schema> {
    /**
     * Discovers the JSON Schema for a given business object using an optional schema ID.
     *
     * @param businessObject The object to find schema for
     * @param schemaIdSupplier Optional supplier that provides schema ID (e.g., from Kafka headers)
     * @return The Schema instance
     * @throws pi2schema.schema.SchemaNotFoundException if no schema can be found for the object
     */
    @Override
    Schema schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier);

    /**
     * Discovers the JSON Schema for a given business object without additional context.
     *
     * @param businessObject The object to find schema for
     * @return The Schema instance
     * @throws pi2schema.schema.SchemaNotFoundException if no schema can be found for the object
     */
    @Override
    default Schema schemaFor(Object businessObject) {
        return schemaFor(businessObject, null);
    }
}
