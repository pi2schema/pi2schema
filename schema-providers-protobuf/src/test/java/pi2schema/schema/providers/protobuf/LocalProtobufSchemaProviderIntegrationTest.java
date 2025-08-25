package pi2schema.schema.providers.protobuf;

import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import com.google.protobuf.Descriptors.Descriptor;
import org.junit.jupiter.api.Test;
import pi2schema.schema.SchemaNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test for LocalProtobufSchemaProvider.
 */
class LocalProtobufSchemaProviderIntegrationTest {

    @Test
    void shouldExtractDescriptorFromProtobufMessage() {
        // Given
        LocalProtobufSchemaProvider provider = new LocalProtobufSchemaProvider();
        Fruit fruitMessage = FruitFixture.waterMelon().build();

        // When
        Descriptor result = provider.schemaFor(fruitMessage);

        // Then
        assertNotNull(result);
        assertEquals("Fruit", result.getName());
        assertEquals("com.acme", result.getFile().getPackage());
        assertEquals(fruitMessage.getDescriptorForType(), result);
    }

    @Test
    void shouldThrowExceptionForNullObject() {
        // Given
        LocalProtobufSchemaProvider provider = new LocalProtobufSchemaProvider();

        // When & Then
        SchemaNotFoundException exception = assertThrows(SchemaNotFoundException.class, () -> provider.schemaFor(null));

        assertEquals("Business object cannot be null", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionForNonProtobufObject() {
        // Given
        LocalProtobufSchemaProvider provider = new LocalProtobufSchemaProvider();
        String nonProtobufObject = "Not a protobuf message";

        // When & Then
        SchemaNotFoundException exception = assertThrows(
            SchemaNotFoundException.class,
            () -> provider.schemaFor(nonProtobufObject)
        );

        assertTrue(exception.getMessage().contains("Object is not a Protobuf Message"));
        assertTrue(exception.getMessage().contains("java.lang.String"));
    }
}
