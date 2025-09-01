package pi2schema.schema.providers.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificRecord;
import pi2schema.schema.SchemaNotFoundException;

/**
 * Local Avro schema provider that extracts Schema information
 * from Avro SpecificRecord and GenericRecord objects using only local capabilities.
 * This provider maintains compatibility with existing Avro implementations
 * by avoiding Schema Registry dependencies.
 *
 * <p>The provider operates in local-only mode, extracting schema information
 * directly from the business object using the standard Avro record
 * interfaces {@code getSchema()} method.</p>
 *
 * <p>Context parameters are ignored to maintain local behavior
 * and backward compatibility with existing implementations.</p>
 */
public class LocalAvroSchemaProvider implements AvroSchemaProvider {

    /**
     * Discovers the schema for a given Avro record object using local extraction.
     * The context parameter is ignored as this provider operates in local-only mode.
     *
     * @param businessObject The Avro record object (SpecificRecord or GenericRecord) to extract schema from
     * @param context Optional context that provides additional information (ignored for local operation)
     * @return The Schema instance extracted from the record
     * @throws SchemaNotFoundException if the business object is null or not an Avro record
     */
    @Override
    public Schema schemaFor(Object businessObject, Object context) {
        // Local-only implementation - context is ignored
        return extractSchemaFromRecord(businessObject);
    }

    /**
     * Extracts the Schema from an Avro record object using local reflection.
     *
     * @param businessObject The object to extract the Schema from
     * @return The Schema instance
     * @throws SchemaNotFoundException if the object is null or not an Avro record
     */
    private Schema extractSchemaFromRecord(Object businessObject) {
        if (businessObject == null) {
            throw new SchemaNotFoundException("Business object cannot be null");
        }

        if (businessObject instanceof SpecificRecord specificRecord) {
            return specificRecord.getSchema();
        } else if (businessObject instanceof GenericRecord genericRecord) {
            return genericRecord.getSchema();
        } else {
            throw new SchemaNotFoundException(
                "Object is not an Avro record (SpecificRecord or GenericRecord): " + businessObject.getClass().getName()
            );
        }
    }
}
