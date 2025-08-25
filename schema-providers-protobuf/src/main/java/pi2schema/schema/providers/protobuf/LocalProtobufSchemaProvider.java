package pi2schema.schema.providers.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import pi2schema.schema.SchemaNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Local Protobuf schema provider that extracts Descriptor information
 * from Protobuf Message objects using only local reflection capabilities.
 * This provider maintains compatibility with existing protobuf implementations
 * by avoiding Schema Registry dependencies.
 *
 * <p>The provider operates in local-only mode, extracting schema information
 * directly from the business object using the standard Protobuf Message
 * interface {@code getDescriptorForType()} method.</p>
 *
 * <p>Schema ID supplier parameters are ignored to maintain local behavior
 * and backward compatibility with existing implementations.</p>
 */
public class LocalProtobufSchemaProvider implements ProtobufSchemaProvider {

    /**
     * Discovers the schema for a given Protobuf Message object using local extraction.
     * The schema ID supplier parameter is ignored as this provider operates in local-only mode.
     *
     * @param businessObject The Protobuf Message object to extract schema from
     * @param schemaIdSupplier Optional supplier that provides schema ID (ignored for local operation)
     * @return The Descriptor instance extracted from the Message
     * @throws SchemaNotFoundException if the business object is null or not a Protobuf Message
     */
    @Override
    public Descriptor schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        // Local-only implementation - schema ID supplier is ignored
        return extractDescriptorFromMessage(businessObject);
    }

    /**
     * Extracts the Descriptor from a Protobuf Message object using local reflection.
     *
     * @param businessObject The object to extract the Descriptor from
     * @return The Descriptor instance
     * @throws SchemaNotFoundException if the object is null or not a Protobuf Message
     */
    private Descriptor extractDescriptorFromMessage(Object businessObject) {
        if (businessObject == null) {
            throw new SchemaNotFoundException("Business object cannot be null");
        }

        if (!(businessObject instanceof Message message)) {
            throw new SchemaNotFoundException(
                "Object is not a Protobuf Message: " + businessObject.getClass().getName()
            );
        }

        return message.getDescriptorForType();
    }
}
