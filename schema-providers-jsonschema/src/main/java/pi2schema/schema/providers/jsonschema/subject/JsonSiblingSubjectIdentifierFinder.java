package pi2schema.schema.providers.jsonschema.subject;

import pi2schema.schema.providers.jsonschema.json.JsonField;
import pi2schema.schema.subject.SubjectIdentifierFinder;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;

import static pi2schema.schema.providers.jsonschema.subject.JsonSubjectIdentifierFieldDefinition.isSubjectIdentifier;

/**
 * Finds subject identifier in the sibling fields of a JSON Schema property.
 */
public class JsonSiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<JsonField> {

    @Override
    public JsonSubjectIdentifierFieldDefinition find(JsonField field) {
        return field
            .parent()
            .properties()
            .stream()
            .filter(e -> isSubjectIdentifier(e.getValue()))
            .map(field::child)
            .map(JsonField::absolutPath)
            .findFirst()
            .map(JsonSubjectIdentifierFieldDefinition::new)
            .orElseThrow(() ->
                new SubjectIdentifierNotFoundException(JsonSiblingSubjectIdentifierFinder.class, field.absolutPath())
            );
    }
}
