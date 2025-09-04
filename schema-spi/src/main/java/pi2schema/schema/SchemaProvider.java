package pi2schema.schema;

/**
 * Generic interface for schema discovery from business objects.
 * Implementations should provide schema lookup strategies specific to their schema format.
 *
 * @param <S> The schema type (e.g., JsonSchema, org.apache.avro.Schema, Descriptor)
 */
public interface SchemaProvider<S> {
    /**
     * Discovers the schema for a given business object using optional context information.
     *
     * @param businessObject The object to find schema for
     * @param context Optional context that provides additional information for schema discovery.
     *                Different adapters can provide different context types (e.g., ConsumerRecord for Kafka,
     *                SQS Message for AWS, etc.)
     * @return The schema instance
     * @throws SchemaNotFoundException if no schema can be found for the object
     */
    S schemaFor(Object businessObject, Object context);

    /**
     * Discovers the schema for a given business object without additional context.
     * Default implementation calls the main method with null context.
     *
     * @param businessObject The object to find schema for
     * @return The schema instance
     * @throws SchemaNotFoundException if no schema can be found for the object
     */
    @Deprecated
    default S schemaFor(Object businessObject) {
        return schemaFor(businessObject, null);
    }
}
