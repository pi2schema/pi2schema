package pi2schema.schema.providers.jsonschema.subject;

import com.acme.FarmerRegisteredEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    void testSubjectFromReturnsEmptyString() throws Exception {
        var def = new JsonSubjectIdentifierFieldDefinition("uuid");

        var farmerRegisteredEvent = Instancio.create(FarmerRegisteredEvent.class);

        assertThat(def.subjectFrom(farmerRegisteredEvent)).isEqualTo(farmerRegisteredEvent.getUuid());
    }
}
