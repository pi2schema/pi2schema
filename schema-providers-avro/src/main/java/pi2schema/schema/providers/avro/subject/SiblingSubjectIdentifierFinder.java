package pi2schema.schema.providers.avro.subject;

import pi2schema.schema.subject.SubjectIdentifierFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierFinder;

public class SiblingSubjectIdentifierFinder implements SubjectIdentifierFinder<Object> {

  @Override
  public SubjectIdentifierFieldDefinition<?> find(Object fieldDescriptor) {
    return null;
  }
}
