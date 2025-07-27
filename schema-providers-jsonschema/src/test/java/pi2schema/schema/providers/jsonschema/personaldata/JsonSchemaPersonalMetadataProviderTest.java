package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaPersonalMetadataProviderTest {

    private JsonSchemaPersonalMetadataProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        provider = new JsonSchemaPersonalMetadataProvider();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldReturnNoEncryptionRequiredForSchemaWithoutPiiFields() throws Exception {
        String schemaString =
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
              }
            }
            """;

        var schema = objectMapper.readTree(schemaString);

        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void shouldIdentifyPiiFieldsInSchemaWithPersonalDataExtension() throws Exception {
        var schema = objectMapper.readTree(getClass().getResourceAsStream("/jsonschema/farmer-registered-event.json"));
        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isTrue();
        List<JsonPersonalDataFieldDefinition<?>> personalDataFields = metadata.getPersonalDataFields();
        assertThat(personalDataFields).hasSize(2);

        assertThat(personalDataFields.get(0).getFieldPath()).isEqualTo("phoneNumber");
        assertThat(personalDataFields.get(1).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldCacheMetadataForRepeatedSchemaAnalysis() throws IOException {
        var schema = objectMapper.readTree(getClass().getResourceAsStream("/jsonschema/farmer-registered-event.json"));

        var metadata1 = provider.forSchema(schema);
        var metadata2 = provider.forSchema(schema);

        // Should return the same cached instance
        assertThat(metadata1).isSameAs(metadata2);
    }

    @Test
    @org.junit.jupiter.api.Disabled(
        "Nested field support temporarily removed - will be re-enabled when nested support is added"
    )
    void shouldAnalyzeNestedPiiFields() throws JsonProcessingException {
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "user": {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "email": {
                          "type": "string",
                          "pi2schema-personal-data": true
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

        var metadata = provider.forSchema(objectMapper.readTree(schema));

        assertThat(metadata.requiresEncryption()).isTrue();
        List<JsonPersonalDataFieldDefinition<?>> personalDataFields = metadata.getPersonalDataFields();
        assertThat(personalDataFields).hasSize(1);
        assertThat(personalDataFields.get(0).getFieldPath()).isEqualTo("user.profile.email");
    }
}
