package pi2schema.schema.personaldata;

/**
 * Generates the @PersonalMetadata abstraction for a given original object.
 * Implementations are intended to be able to encapsulate the retrieval the schema from
 * the original object.
 *
 * @param <T> original object type.
 */
public interface PersonalMetadataProvider<T> {
    PersonalMetadata<T> forType(T originalObject);
}
