package pi2schema.schema.providers.jsonschema.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating end-to-end JSON Schema PII handling.
 * Tests the simplified implementation that supports direct field annotation only.
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
        // Given: JSON Schema with direct PII annotations
        String schema = """
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

        // Then: Should require encryption and identify PII field
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldNotRequireEncryptionForSchemaWithoutPiiFields() throws Exception {
        // Given: Schema without PII fields
        String schema = """
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

        // Then: No encryption should be required
        assertThat(metadata.requiresEncryption()).isFalse();
        assertThat(metadata.getPersonalDataFields()).isEmpty();
    }

    @Test
    void shouldHandleMultiplePiiFields() throws Exception {
        // Given: Schema with multiple PII fields
        String schema = """
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

        // Then: Should identify both PII fields
        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(2);
        
        var fieldPaths = metadata.getPersonalDataFields().stream()
            .map(field -> field.getFieldPath())
            .toList();
        assertThat(fieldPaths).containsExactlyInAnyOrder("email", "phone");
    }
}
