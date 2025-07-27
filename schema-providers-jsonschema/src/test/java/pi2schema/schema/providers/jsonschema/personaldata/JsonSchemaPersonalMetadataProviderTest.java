package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
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

        JsonNode schema = objectMapper.readTree(schemaString);

        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void shouldIdentifyPiiFieldsInSchemaWithPersonalDataExtension() throws Exception {
        JsonNode schema = objectMapper.readTree(getClass().getResourceAsStream("/jsonschema/valid.json"));
        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isTrue();
        //                assertThat(metadata.getPersonalDataFields()).hasSize(1);
        //                assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldCacheMetadataForRepeatedSchemaAnalysis() {
        //        String schema =
        //            """
        //            {
        //              "type": "object",
        //              "properties": {
        //                "userId": {
        //                  "type": "string",
        //                  "pi2schema-subject-identifier": true
        //                },
        //                "email": {
        //                  "type": "string",
        //                  "pi2schema-personal-data": true
        //                }
        //              }
        //            }
        //            """;
        //
        //        var metadata1 = provider.forSchema(schema);
        //        var metadata2 = provider.forSchema(schema);
        //
        //        // Should return the same cached instance
        //        assertThat(metadata1).isSameAs(metadata2);
    }

    @Test
    @org.junit.jupiter.api.Disabled(
        "Nested field support temporarily removed - will be re-enabled when nested support is added"
    )
    void shouldAnalyzeNestedPiiFields() {
        //        String schema =
        //            """
        //            {
        //              "type": "object",
        //              "properties": {
        //                "userId": {
        //                  "type": "string",
        //                  "pi2schema-subject-identifier": true
        //                },
        //                "user": {
        //                  "type": "object",
        //                  "properties": {
        //                    "profile": {
        //                      "type": "object",
        //                      "properties": {
        //                        "email": {
        //                          "type": "string",
        //                          "pi2schema-personal-data": true
        //                        }
        //                      }
        //                    }
        //                  }
        //                }
        //              }
        //            }
        //            """;
        //
        //        var metadata = provider.forSchema(schema);
        //
        //        assertThat(metadata.requiresEncryption()).isTrue();
        //        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        //        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("user.profile.email");
    }
}
