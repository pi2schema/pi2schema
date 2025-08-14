package pi2schema.schema.personaldata;

/**
 * Generates the @PersonalMetadata abstraction for a given schema definition.
 * Implementations should analyze the provided schema to identify PII fields.
 *
 * @param <T> original object type.
 * @param <S> schema definition type.
 */
public interface PersonalMetadataProvider<T, S> {
    /**
     * Creates PersonalMetadata for a given schema definition.
     * This is the method for schema-based providers.
     * @param schema The schema definition to analyze
     * @return PersonalMetadata containing PII field definitions
     */
    PersonalMetadata<T> forSchema(S schema);

    /**
     * @deprecated Use {@link #forSchema(Object)} instead. This method will be removed in future versions.
     */
    @Deprecated
    PersonalMetadata<T> forType(T originalObject);
}
