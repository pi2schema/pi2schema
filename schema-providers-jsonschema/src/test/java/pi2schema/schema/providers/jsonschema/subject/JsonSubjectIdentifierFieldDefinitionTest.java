package pi2schema.schema.providers.jsonschema.subject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.schema.providers.jsonschema.model.FarmerRegisteredEvent;
import pi2schema.schema.subject.SubjectIdentifierRetrievalException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonSubjectIdentifierFieldDefinition covering subject identifier extraction
 * and error handling scenarios (AC-002, AC-008).
 */
class JsonSubjectIdentifierFieldDefinitionTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testIsSubjectIdentifierTrue() throws Exception {
        String json =
            """
            {
                "pi2schema-subject-identifier": true
            }
            """;
        JsonNode node = objectMapper.readTree(json);

        assertTrue(JsonSubjectIdentifierFieldDefinition.isSubjectIdentifier(node));
    }

    @Test
    void testIsSubjectIdentifierFalseWhenSubjectIdentifierExtensionNotPresent() throws Exception {
        String json =
            """
            {
                "someOtherField": "value"
            }
            """;
        JsonNode node = objectMapper.readTree(json);

        assertFalse(JsonSubjectIdentifierFieldDefinition.isSubjectIdentifier(node));
    }

    @Test
    void testIsSubjectIdentifierFalseWhenExtensionIsFalse() throws Exception {
        String json =
            """
            {
                "pi2schema-subject-identifier": false
            }
            """;
        JsonNode node = objectMapper.readTree(json);

        assertFalse(JsonSubjectIdentifierFieldDefinition.isSubjectIdentifier(node));
    }

    @Test
    void testIsSubjectIdentifierFalseWhenExtensionIsNotBoolean() throws Exception {
        String json =
            """
            {
                "pi2schema-subject-identifier": 0
            }
            """;
        JsonNode node = objectMapper.readTree(json);

        assertFalse(JsonSubjectIdentifierFieldDefinition.isSubjectIdentifier(node));
    }

    @Test
    void testSubjectFromReturnsCorrectValue() throws Exception {
        var def = new JsonSubjectIdentifierFieldDefinition("uuid");
        var farmerRegisteredEvent = Instancio.create(FarmerRegisteredEvent.class);

        assertThat(def.subjectFrom(farmerRegisteredEvent)).isEqualTo(farmerRegisteredEvent.getUuid());
    }

    @Test
    void shouldExtractSubjectIdentifierFromBusinessObject() {
        // Given: A business object with subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "john@example.com");

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When: Extract subject identifier
        String result = def.subjectFrom(businessObject);

        // Then: Should return the subject identifier (AC-002)
        assertThat(result).isEqualTo("user-123");
    }

    @Test
    void shouldThrowExceptionWhenSubjectIdentifierMissing() {
        // Given: A business object without subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("email", "john@example.com");

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When/Then: Should throw exception for missing subject identifier (AC-008)
        assertThrows(
            SubjectIdentifierRetrievalException.class,
            () -> {
                def.subjectFrom(businessObject);
            }
        );
    }

    @Test
    void shouldThrowExceptionWhenSubjectIdentifierIsNull() {
        // Given: A business object with null subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", null);
        businessObject.put("email", "john@example.com");

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When/Then: Should throw exception for null subject identifier (AC-008)
        assertThrows(
            SubjectIdentifierRetrievalException.class,
            () -> {
                def.subjectFrom(businessObject);
            }
        );
    }

    @Test
    void shouldExtractStringSubjectIdentifier() {
        // Given: A business object with string subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "string-user-456");

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When: Extract subject identifier
        String result = def.subjectFrom(businessObject);

        // Then: Should return string value
        assertThat(result).isEqualTo("string-user-456");
    }

    @Test
    void shouldExtractNumericSubjectIdentifier() {
        // Given: A business object with numeric subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", 12345);

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When: Extract subject identifier
        String result = def.subjectFrom(businessObject);

        // Then: Should return string representation of number
        assertThat(result).isEqualTo("12345");
    }

    @Test
    void shouldHandleNestedPropertyPath() {
        // Given: A business object with nested structure
        Map<String, Object> nested = new HashMap<>();
        nested.put("id", "nested-123");

        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("profile", nested);

        var def = new JsonSubjectIdentifierFieldDefinition("profile.id");

        // When: Extract subject identifier from nested path
        String result = def.subjectFrom(businessObject);

        // Then: Should return nested value
        assertThat(result).isEqualTo("nested-123");
    }

    @Test
    void shouldHandleNonStringObjectTypes() {
        // Given: A business object with boolean subject identifier
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", true);

        var def = new JsonSubjectIdentifierFieldDefinition("userId");

        // When: Extract subject identifier
        String result = def.subjectFrom(businessObject);

        // Then: Should convert to string representation
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldThrowExceptionForInvalidPropertyPath() {
        // Given: A business object without the specified nested path
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");

        var def = new JsonSubjectIdentifierFieldDefinition("profile.nonexistent");

        // When/Then: Should throw exception for invalid property path (AC-008)
        assertThrows(
            SubjectIdentifierRetrievalException.class,
            () -> {
                def.subjectFrom(businessObject);
            }
        );
    }
}
