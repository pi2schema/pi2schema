package pi2schema.schema.providers.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import pi2schema.schema.SchemaProvider;

/**
 * Protobuf schema provider SPI.
 */
public interface ProtobufSchemaProvider extends SchemaProvider<Descriptor> {
    // Inherits schemaFor methods with Descriptor return type
}
