package pi2schema.schema;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Generic interface for schema discovery from business objects.
 * Implementations should provide schema lookup strategies specific to their schema format.
 *
 * @param <S> The schema type (e.g., JsonSchema, org.apache.avro.Schema, Descriptor)
 */
public interface SchemaProvider<S> {
    /**
     * Discovers the schema for a given business object using an optional schema identifier.
     *
     * @param businessObject The object to find schema for
     * @param schemaIdSupplier Optional supplier that provides schema ID (e.g., from Kafka headers)
     * @return The schema instance
     * @throws SchemaNotFoundException if no schema can be found for the object
     */
    S schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier);

    /**
     * Discovers the schema for a given business object without additional context.
     * Default implementation calls the main method with null supplier.
     *
     * @param businessObject The object to find schema for
     * @return The schema instance
     * @throws SchemaNotFoundException if no schema can be found for the object
     */
    default S schemaFor(Object businessObject) {
        return schemaFor(businessObject, null);
    }
}
