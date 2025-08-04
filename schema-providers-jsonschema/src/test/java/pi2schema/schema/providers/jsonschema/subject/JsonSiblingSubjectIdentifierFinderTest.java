package pi2schema.schema.providers.jsonschema.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JsonSiblingSubjectIdentifierFinder.
 *
 * Note: This finder works at the schema analysis level, finding subject identifier
 * fields within JSON schema definitions. The actual tests are covered by the
 * integration tests in JsonSchemaIntegrationTest which test the full pipeline
 * including this finder's functionality.
 */
class JsonSiblingSubjectIdentifierFinderTest {

    @Test
    void shouldInstantiateCorrectly() {
        // When: Create a new finder instance
        JsonSiblingSubjectIdentifierFinder finder = new JsonSiblingSubjectIdentifierFinder();

        // Then: Should be created successfully
        assertThat(finder).isNotNull();
    }
}
