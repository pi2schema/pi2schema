package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PersonalMetadata for JSON Schema.
 * Handles encryption/decryption operations for business objects containing PII fields.
 *
 * NOTE: This version supports simple top-level field access only.
 * Nested field support will be added in future versions.
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
        T encryptingInstance = (T) copyBusinessObject(decryptedInstance);

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
        @SuppressWarnings("unchecked")
        T decryptingInstance = (T) copyBusinessObject(encryptedInstance);

        CompletableFuture<Void>[] futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToDecrypted(decryptor, decryptingInstance))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return decryptingInstance;
    }

    /**
     * Creates a copy of the business object for mutation during encryption/decryption.
     * Uses Jackson's ObjectMapper for deep copying.
     */
    @SuppressWarnings("unchecked")
    private Object copyBusinessObject(T original) {
        // For simple copying, we can use Jackson to serialize and deserialize
        // This works for most business objects that are JSON-serializable
        try {
            // Get the class from the instance
            Class<?> clazz = original.getClass();

            // If it's a Map, do simple HashMap copy
            if (original instanceof Map) {
                return new HashMap<>((Map<String, Object>) original);
            }

            // For other business objects, use Jackson for deep copy
            // This requires a default ObjectMapper - personal data fields should have their own
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(original);
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy business object for encryption/decryption", e);
        }
    }

    public List<JsonPersonalDataFieldDefinition<T>> getPersonalDataFields() {
        return personalDataFields;
    }
}
