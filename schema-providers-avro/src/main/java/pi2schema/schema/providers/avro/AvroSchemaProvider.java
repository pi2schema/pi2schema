package pi2schema.schema.providers.avro;

import org.apache.avro.Schema;
import pi2schema.schema.SchemaProvider;

/**
 * Avro schema provider SPI.
 */
public interface AvroSchemaProvider extends SchemaProvider<Schema> {
    // Inherits schemaFor methods with Avro Schema return type
}
