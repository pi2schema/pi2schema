package pi2schema.schema.providers.jsonschema.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.SubjectIdentifierRetrievalException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration test demonstrating end-to-end JSON Schema PII handling.
 * Tests the simplified implementation that supports direct field annotation only.
 * Covers acceptance criteria AC-001 through AC-009.
 */
class JsonSchemaIntegrationTest {

    private JsonSchemaPersonalMetadataProvider<Map<String, Object>> provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        provider = new JsonSchemaPersonalMetadataProvider<>();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldIdentifyPiiFieldsInSimpleSchema() throws Exception {
        // Given: JSON Schema with direct PII annotations (AC-001)
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "format": "email",
                  "pi2schema-personal-data": true
                },
                "name": {
                  "type": "string"
                }
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should require encryption and identify PII field (AC-001)
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldNotRequireEncryptionForSchemaWithoutPiiFields() throws Exception {
        // Given: Schema without PII fields (AC-006)
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: No encryption should be required (AC-006)
        assertThat(metadata.requiresEncryption()).isFalse();
        assertThat(metadata.getPersonalDataFields()).isEmpty();
    }

    @Test
    void shouldHandleMultiplePiiFields() throws Exception {
        // Given: Schema with multiple PII fields (AC-005)
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                },
                "phone": {
                  "type": "string",
                  "pi2schema-personal-data": true
                },
                "name": {
                  "type": "string"
                }
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should identify both PII fields (AC-005)
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(2);

        var fieldPaths = metadata.getPersonalDataFields().stream().map(field -> field.getFieldPath()).toList();
        assertThat(fieldPaths).containsExactlyInAnyOrder("email", "phone");
    }

    @Test
    void shouldThrowExceptionWhenSubjectIdentifierMissing() throws Exception {
        // Given: Schema with PII field but no subject identifier (AC-008)
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                },
                "name": {
                  "type": "string"
                }
              }
            }
            """;

        // When/Then: Should throw SubjectIdentifierNotFoundException (AC-008)
        assertThrows(
            SubjectIdentifierNotFoundException.class,
            () -> {
                provider.forSchema(objectMapper.readTree(schema));
            }
        );
    }

    @Test
    @DisplayName("Should extract subject identifier from business object (AC-002)")
    void shouldExtractSubjectIdentifierFromBusinessObject() throws Exception {
        // Given: Schema with subject identifier and business object
        String schemaJson =
            """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                }
              }
            }
            """;

        JsonNode schema = objectMapper.readTree(schemaJson);
        var metadata = provider.forSchema(schema);

        // When: Extract subject identifier from business object
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "john@example.com");

        var personalDataField = metadata.getPersonalDataFields().get(0);
        // Access the subject identifier field through the personal data field
        String subjectId = personalDataField.getSubjectIdentifierDefinition().subjectFrom(businessObject);

        // Then: Should extract correct subject identifier (AC-002)
        assertThat(subjectId).isEqualTo("user-123");
    }

    @Test
    void shouldThrowExceptionWhenSubjectIdentifierValueMissing() throws Exception {
        // Given: Schema with subject identifier field but business object without it
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                }
              }
            }
            """;

        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // When: Create business object without subject identifier value
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("email", "john@example.com");

        var personalDataField = metadata.getPersonalDataFields().get(0);

        // Then: Should throw exception when trying to extract missing subject identifier (AC-008)
        assertThrows(
            SubjectIdentifierRetrievalException.class,
            () -> {
                personalDataField.getSubjectIdentifierDefinition().subjectFrom(businessObject);
            }
        );
    }

    @Test
    void shouldHandleEmptySchema() throws Exception {
        // Given: Empty or basic schema
        String schema =
            """
            {
              "type": "object",
              "properties": {}
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should not require encryption
        assertThat(metadata.requiresEncryption()).isFalse();
        assertThat(metadata.getPersonalDataFields()).isEmpty();
    }

    @Test
    void shouldIgnoreFieldsWithoutPiiExtension() throws Exception {
        // Given: Schema with mixed field types
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                },
                "normalField": {
                  "type": "string"
                },
                "anotherField": {
                  "type": "string",
                  "description": "Just a regular field"
                }
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should only identify explicitly marked PII fields
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldIgnoreFieldsWithFalsePiiExtension() throws Exception {
        // Given: Schema with false PII extension
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                },
                "pseudoPii": {
                  "type": "string",
                  "pi2schema-personal-data": false
                }
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should only identify fields with true PII extension
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldCacheMetadataForSameSchema() throws Exception {
        // Given: Same schema content
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "type": "string",
                  "pi2schema-personal-data": true
                }
              }
            }
            """;

        // When: Analyze same schema twice
        var metadata1 = provider.forSchema(objectMapper.readTree(schema));
        var metadata2 = provider.forSchema(objectMapper.readTree(schema));

        // Then: Should return same cached instance
        assertThat(metadata1).isSameAs(metadata2);
    }
}
