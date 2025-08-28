package pi2schema.serialization.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.schema.SchemaNotFoundException;
import pi2schema.schema.providers.jsonschema.JsonSchemaProvider;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Test-specific JSON Schema provider that provides a hardcoded schema for testing.
 */
public class TestJsonSchemaProvider implements JsonSchemaProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JsonNode schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        return schemaFor(businessObject);
    }

    @Override
    public JsonNode schemaFor(Object businessObject) {
        if (businessObject instanceof Map) {
            try {
                // Return a test schema for user data with PII fields
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
                        },
                        "name": {
                          "type": "string",
                          "pi2schema-personal-data": true
                        },
                        "favoriteNumber": {
                          "type": "integer"
                        }
                      }
                    }
                    """;
                return objectMapper.readTree(schemaJson);
            } catch (Exception e) {
                throw new SchemaNotFoundException("Failed to parse test schema", e);
            }
        }
        throw new SchemaNotFoundException("No schema found for object: " + businessObject.getClass());
    }
}
