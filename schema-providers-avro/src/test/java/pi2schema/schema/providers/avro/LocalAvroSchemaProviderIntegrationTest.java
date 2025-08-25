package pi2schema.schema.providers.avro;

import com.acme.UserValid;
import com.acme.UserValidFixture;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pi2schema.schema.SchemaNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LocalAvroSchemaProvider implementation.
 * Tests cover all acceptance criteria from the specification.
 */
class LocalAvroSchemaProviderIntegrationTest {

    private LocalAvroSchemaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalAvroSchemaProvider();
    }

    @Test
    @DisplayName("AC-001: Given an Avro SpecificRecord object, When schemaFor() is called, Then it returns the correct Schema from getSchema()")
    void shouldReturnCorrectSchemaFromSpecificRecord() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When
        Schema result = provider.schemaFor(userRecord, null);

        // Then
        assertNotNull(result);
        assertEquals(userRecord.getSchema(), result);
        assertEquals("UserValid", result.getName());
        assertEquals("com.acme", result.getNamespace());
        assertEquals(Schema.Type.RECORD, result.getType());
    }

    @Test
    @DisplayName("AC-002: Given an Avro GenericRecord object, When schemaFor() is called, Then it returns the correct Schema from getSchema()")
    void shouldReturnCorrectSchemaFromGenericRecord() {
        // Given
        UserValid specificRecord = UserValidFixture.johnDoe().build();
        Schema schema = specificRecord.getSchema();

        GenericRecord genericRecord = new GenericData.Record(schema);
        genericRecord.put("uuid", "test-uuid-123");
        genericRecord.put("favorite_number", 42);
        genericRecord.put("email", "test@example.com");

        // When
        Schema result = provider.schemaFor(genericRecord, null);

        // Then
        assertNotNull(result);
        assertEquals(genericRecord.getSchema(), result);
        assertEquals("UserValid", result.getName());
        assertEquals("com.acme", result.getNamespace());
        assertEquals(Schema.Type.RECORD, result.getType());
    }

    @Test
    @DisplayName("AC-003: Given a schema ID supplier parameter, When schemaFor() is called, Then it ignores the schema ID and uses local extraction")
    void shouldIgnoreSchemaIdSupplierAndUseLocalExtraction() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();
        Supplier<Optional<Integer>> schemaIdSupplier = () -> Optional.of(456);

        // When
        Schema result1 = provider.schemaFor(userRecord, schemaIdSupplier);
        Schema result2 = provider.schemaFor(userRecord, null);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertSame(result1, result2, "Both calls should return the same schema instance");
        assertEquals(userRecord.getSchema(), result1);
        assertEquals(userRecord.getSchema(), result2);
    }

    @Test
    @DisplayName("AC-004: Given a non-Avro object, When schemaFor() is called, Then it throws SchemaNotFoundException with clear error message")
    void shouldThrowSchemaNotFoundExceptionForNonAvroObject() {
        // Given
        String nonAvroObject = "This is not an Avro record";

        // When & Then
        SchemaNotFoundException exception = assertThrows(
            SchemaNotFoundException.class,
            () -> provider.schemaFor(nonAvroObject, null)
        );

        assertTrue(exception.getMessage().contains("Object is not an Avro record"));
        assertTrue(exception.getMessage().contains("SpecificRecord or GenericRecord"));
        assertTrue(exception.getMessage().contains("java.lang.String"));
    }

    @Test
    @DisplayName("AC-005: Given a null business object, When schemaFor() is called, Then it throws SchemaNotFoundException gracefully")
    void shouldThrowSchemaNotFoundExceptionForNullObject() {
        // Given
        Object nullObject = null;

        // When & Then
        SchemaNotFoundException exception = assertThrows(
            SchemaNotFoundException.class,
            () -> provider.schemaFor(nullObject, null)
        );

        assertEquals("Business object cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("REQ-007: Provider should be thread-safe for concurrent access")
    void shouldBeThreadSafeForConcurrentAccess() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    Schema result = provider.schemaFor(userRecord, null);
                    assertNotNull(result);
                    assertEquals(userRecord.getSchema(), result);
                }
            }, executor);
        }

        // Then
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        });

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should handle various SpecificRecord types")
    void shouldHandleVariousSpecificRecordTypes() {
        // Given
        UserValid userRecord1 = UserValidFixture.johnDoe().build();
        UserValid userRecord2 = UserValid.newBuilder()
            .setUuid("another-uuid")
            .setFavoriteNumber(100)
            .setEmail("another@email.com")
            .build();

        // When
        Schema result1 = provider.schemaFor(userRecord1, null);
        Schema result2 = provider.schemaFor(userRecord2, null);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertSame(result1, result2, "Same record type should return same schema instance");
        assertEquals("UserValid", result1.getName());
        assertEquals("UserValid", result2.getName());
    }

    @Test
    @DisplayName("Should work with default schemaFor method without supplier")
    void shouldWorkWithDefaultSchemaForMethod() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When
        Schema result = provider.schemaFor(userRecord);

        // Then
        assertNotNull(result);
        assertEquals(userRecord.getSchema(), result);
    }

    @Test
    @DisplayName("Should provide schemas suitable for field analysis")
    void shouldProvideSchemasSuitableForFieldAnalysis() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When
        Schema result = provider.schemaFor(userRecord, null);

        // Then
        assertNotNull(result);
        assertEquals(Schema.Type.RECORD, result.getType());
        assertTrue(result.getFields().size() > 0, "Schema should contain field information");

        // Verify specific fields from the UserValid schema
        assertNotNull(result.getField("uuid"));
        assertNotNull(result.getField("favorite_number"));
        assertNotNull(result.getField("email"));
    }

    @Test
    @DisplayName("Should handle union types correctly")
    void shouldHandleUnionTypesCorrectly() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When
        Schema result = provider.schemaFor(userRecord, null);

        // Then
        assertNotNull(result);

        // Check that union fields are properly represented
        Schema.Field favoriteNumberField = result.getField("favorite_number");
        assertNotNull(favoriteNumberField);
        assertEquals(Schema.Type.UNION, favoriteNumberField.schema().getType());

        Schema.Field emailField = result.getField("email");
        assertNotNull(emailField);
        assertEquals(Schema.Type.UNION, emailField.schema().getType());
    }

    @Test
    @DisplayName("Should maintain compatibility with Avro interface")
    void shouldMaintainCompatibilityWithAvroInterface() {
        // Given
        UserValid userRecord = UserValidFixture.johnDoe().build();

        // When - Test with both SpecificRecord and Object interfaces
        Schema resultFromSpecific = provider.schemaFor((org.apache.avro.specific.SpecificRecord) userRecord, null);
        Schema resultFromObject = provider.schemaFor((Object) userRecord, null);

        // Then
        assertNotNull(resultFromSpecific);
        assertNotNull(resultFromObject);
        assertSame(resultFromSpecific, resultFromObject);
        assertEquals(userRecord.getSchema(), resultFromSpecific);
    }
}
