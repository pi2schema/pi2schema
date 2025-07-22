package pi2schema.schema.providers.jsonschema.personaldata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaPersonalMetadataProviderTest {

    private JsonSchemaPersonalMetadataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JsonSchemaPersonalMetadataProvider();
    }

    @Test
    void shouldReturnNoEncryptionRequiredForSchemaWithoutPiiFields() {
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

        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void shouldIdentifyPiiFieldsInSchemaWithPersonalDataExtension() {
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
                  "oneOf": [
                    {
                      "type": "string",
                      "pi2schema-personal-data": true
                    },
                    {
                      "$ref": "#/$defs/EncryptedPersonalData"
                    }
                  ]
                },
                "name": {"type": "string"}
              },
              "$defs": {
                "EncryptedPersonalData": {
                  "type": "object",
                  "properties": {
                    "subjectId": {"type": "string"},
                    "data": {"type": "string"},
                    "usedTransformation": {"type": "string"},
                    "initializationVector": {"type": "string"}
                  }
                }
              }
            }
            """;

        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldCacheMetadataForRepeatedSchemaAnalysis() {
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

        var metadata1 = provider.forSchema(schema);
        var metadata2 = provider.forSchema(schema);

        // Should return the same cached instance
        assertThat(metadata1).isSameAs(metadata2);
    }

    @Test
    void shouldAnalyzeNestedPiiFields() {
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

        var metadata = provider.forSchema(schema);

        assertThat(metadata.requiresEncryption()).isTrue();
        assertThat(metadata.getPersonalDataFields()).hasSize(1);
        assertThat(metadata.getPersonalDataFields().get(0).getFieldPath()).isEqualTo("user.profile.email");
    }
}
