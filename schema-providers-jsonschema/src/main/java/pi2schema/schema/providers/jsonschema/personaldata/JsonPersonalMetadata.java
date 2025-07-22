package pi2schema.schema.providers.jsonschema.personaldata;

import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.providers.jsonschema.util.JsonObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PersonalMetadata for JSON Schema.
 * Handles encryption/decryption operations for JSON objects containing PII fields.
 */
public class JsonPersonalMetadata implements PersonalMetadata<Map<String, Object>> {

    private final List<JsonPersonalDataFieldDefinition> personalDataFields;

    public JsonPersonalMetadata(List<JsonPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = List.copyOf(personalDataFields);
    }

    @Override
    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    @Override
    public Map<String, Object> swapToEncrypted(Encryptor encryptor, Map<String, Object> decryptedInstance) {
        Map<String, Object> encryptingInstance = JsonObjectUtils.deepCopy(decryptedInstance);

        CompletableFuture<Void>[] futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToEncrypted(encryptor, encryptingInstance))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return encryptingInstance;
    }

    @Override
    public Map<String, Object> swapToDecrypted(Decryptor decryptor, Map<String, Object> encryptedInstance) {
        Map<String, Object> decryptingInstance = JsonObjectUtils.deepCopy(encryptedInstance);

        CompletableFuture<Void>[] futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToDecrypted(decryptor, decryptingInstance))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return decryptingInstance;
    }

    public List<JsonPersonalDataFieldDefinition> getPersonalDataFields() {
        return personalDataFields;
    }
}
