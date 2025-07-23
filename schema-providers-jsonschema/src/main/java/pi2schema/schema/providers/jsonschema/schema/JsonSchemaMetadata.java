package pi2schema.schema.providers.jsonschema.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Holds metadata extracted from JSON Schema analysis.
 * Contains information about PII fields and subject identifiers.
 */
public class JsonSchemaMetadata {

    private final JsonNode schemaNode;
    private final List<String> subjectIdentifierFields;
    private final List<JsonPiiFieldInfo> piiFields;

    public JsonSchemaMetadata(
        JsonNode schemaNode,
        List<String> subjectIdentifierFields,
        List<JsonPiiFieldInfo> piiFields
    ) {
        this.schemaNode = schemaNode;
        this.subjectIdentifierFields = List.copyOf(subjectIdentifierFields);
        this.piiFields = List.copyOf(piiFields);
    }

    public JsonNode getSchemaNode() {
        return schemaNode;
    }

    public List<String> getSubjectIdentifierFields() {
        return subjectIdentifierFields;
    }

    public List<JsonPiiFieldInfo> getPiiFields() {
        return piiFields;
    }

    public boolean hasSubjectIdentifiers() {
        return !subjectIdentifierFields.isEmpty();
    }

    public boolean hasPiiFields() {
        return !piiFields.isEmpty();
    }

    public boolean requiresEncryption() {
        return hasPiiFields();
    }
}
