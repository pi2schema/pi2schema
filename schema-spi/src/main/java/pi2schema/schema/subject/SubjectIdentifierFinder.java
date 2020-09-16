package pi2schema.schema.subject;


public interface SubjectIdentifierFinder<D> {

    /**
     * Introspect the message content in order to identify the {@link SubjectIdentifierFieldDefinition}.
     *
     * @param fieldDescriptor the field descriptor which contains the personal data
     * @return The {@link SubjectIdentifierFieldDefinition} found used in the implemented strategy in which the field
     * definition will be able to extract the subject value at runtime
     * @throws TooManySubjectIdentifiersException case the strategy finds more than one possible subject identifier
     * @throws SubjectIdentifierNotFoundException case the strategy does not find possible subject identifier
     */
    SubjectIdentifierFieldDefinition<?> find(D fieldDescriptor);

}
