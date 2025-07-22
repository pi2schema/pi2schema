package pi2schema.schema.providers.jsonschema.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaAnalyzerTest {

    private JsonSchemaAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JsonSchemaAnalyzer();
    }

    @Test
    void shouldIdentifySubjectIdentifierFields() {
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "name": {"type": "string"}
              }
            }
            """;

        var metadata = analyzer.analyzeSchema(schema);

        assertThat(metadata.getSubjectIdentifierFields()).containsExactly("userId");
        assertThat(metadata.hasSubjectIdentifiers()).isTrue();
    }

    @Test
    void shouldIdentifyPiiFields() {
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

        var metadata = analyzer.analyzeSchema(schema);

        assertThat(metadata.getPiiFields()).hasSize(1);
        assertThat(metadata.getPiiFields().get(0).getFieldPath()).isEqualTo("email");
        assertThat(metadata.hasPiiFields()).isTrue();
    }

    @Test
    void shouldIdentifyOneOfPiiFields() {
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
                }
              },
              "$defs": {
                "EncryptedPersonalData": {
                  "type": "object"
                }
              }
            }
            """;

        var metadata = analyzer.analyzeSchema(schema);

        assertThat(metadata.getPiiFields()).hasSize(1);
        assertThat(metadata.getPiiFields().get(0).getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldIdentifyNestedPiiFields() {
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

        var metadata = analyzer.analyzeSchema(schema);

        assertThat(metadata.getPiiFields()).hasSize(1);
        assertThat(metadata.getPiiFields().get(0).getFieldPath()).isEqualTo("user.profile.email");
    }

    @Test
    void shouldCacheAnalysisResults() {
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"}
              }
            }
            """;

        var metadata1 = analyzer.analyzeSchema(schema);
        var metadata2 = analyzer.analyzeSchema(schema);

        assertThat(metadata1).isSameAs(metadata2);
    }

    @Test
    void shouldThrowExceptionForInvalidSchema() {
        String invalidSchema = "{ invalid json }";

        assertThatThrownBy(() -> analyzer.analyzeSchema(invalidSchema))
            .isInstanceOf(JsonSchemaAnalyzer.JsonSchemaAnalysisException.class)
            .hasMessageContaining("Failed to analyze JSON Schema");
    }

    @Test
    void shouldHandleSchemaWithoutExtensions() {
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

        var metadata = analyzer.analyzeSchema(schema);

        assertThat(metadata.getSubjectIdentifierFields()).isEmpty();
        assertThat(metadata.getPiiFields()).isEmpty();
        assertThat(metadata.requiresEncryption()).isFalse();
    }
}
