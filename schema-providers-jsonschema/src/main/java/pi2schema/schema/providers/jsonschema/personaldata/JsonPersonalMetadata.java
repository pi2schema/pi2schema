package pi2schema.schema.providers.jsonschema.personaldata;

import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PersonalMetadata for JSON Schema.
 * Handles encryption/decryption operations for business objects containing PII fields.
 *
 * This implementation supports direct field annotation with pi2schema-personal-data extension
 * for top-level fields only.
 */
public class JsonPersonalMetadata<T> implements PersonalMetadata<T> {

    private final List<JsonPersonalDataFieldDefinition<T>> personalDataFields;

    public JsonPersonalMetadata(List<JsonPersonalDataFieldDefinition<T>> personalDataFields) {
        this.personalDataFields = List.copyOf(personalDataFields);
    }

    @Override
    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    @Override
    public T swapToEncrypted(Encryptor encryptor, T decryptedInstance) {
        // Create a copy of the business object for mutation
        @SuppressWarnings("unchecked")
        // Note: Direct instance mutation for simplicity in current implementation
        T encryptingInstance = decryptedInstance;

        CompletableFuture<Void>[] futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToEncrypted(encryptor, encryptingInstance))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return encryptingInstance;
    }

    @Override
    public T swapToDecrypted(Decryptor decryptor, T encryptedInstance) {
        // Create a copy of the business object for mutation
        // Note: Direct instance mutation for simplicity in current implementation
        T decryptingInstance = encryptedInstance;

        CompletableFuture<Void>[] futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToDecrypted(decryptor, decryptingInstance))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return decryptingInstance;
    }

    public List<JsonPersonalDataFieldDefinition<T>> getPersonalDataFields() {
        return personalDataFields;
    }
}
