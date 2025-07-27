package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.JsonNode;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;
import pi2schema.schema.providers.jsonschema.subject.JsonSubjectIdentifierFieldDefinition;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PersonalDataFieldDefinition for JSON Schema.
 * Handles encryption and decryption of PII fields in business objects.
 * Simplified to work directly with field path and schema information.
 */
public class JsonPersonalDataFieldDefinition<T> implements PersonalDataFieldDefinition<T> {

    public static final String PERSONAL_DATA_EXTENSION = "pi2schema-personal-data";

    private final String fieldPath;
    private final JsonNode fieldSchema;
    private final JsonSubjectIdentifierFieldDefinition subjectIdentifier;

    public JsonPersonalDataFieldDefinition(
        String fieldPath,
        JsonSubjectIdentifierFieldDefinition jsonSubjectIdentifierFieldDefinition
    ) {
        this.fieldPath = fieldPath;
        this.fieldSchema = null;
        this.subjectIdentifier = jsonSubjectIdentifierFieldDefinition;
    }

    static boolean hasPersonalData(JsonNode field) {
        return field.get(PERSONAL_DATA_EXTENSION) != null && field.get(PERSONAL_DATA_EXTENSION).asBoolean();
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, T decryptedInstance) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                //                @SuppressWarnings("unchecked")
                //                Map<String, Object> objectMap = objectMapper.convertValue(decryptedInstance, Map.class);
                //
                //                Object personalData = getFieldValue(objectMap, fieldPath);
                //                if (personalData == null) {
                //                    return decryptedInstance;
                //                }
                //
                //                String subjectId = subjectIdentifierFinder.findSubjectIdentifier(decryptedInstance, subjectIdentifierFields);
                //
                //                EncryptedData encryptedData = encryptor.encrypt(
                //                        subjectId,
                //                        personalData.toString().getBytes(StandardCharsets.UTF_8)
                //                );
                //
                //                EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
                //                    Base64.getEncoder().encodeToString(encryptedData.getData()),
                //                    Base64.getEncoder().encodeToString(encryptedData.getIv().getIV()),
                //                    encryptedData.getKeyId()
                //                );
                //
                //                setFieldValue(objectMap, fieldPath, encryptedPersonalData);
                //
                //                return objectMapper.convertValue(objectMap, (Class<T>) decryptedInstance.getClass());
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to encrypt field: " + fieldPath, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, T encryptedInstance) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                //                @SuppressWarnings("unchecked")
                //                Map<String, Object> objectMap = objectMapper.convertValue(encryptedInstance, Map.class);
                //
                //                Object encryptedField = getFieldValue(objectMap, fieldPath);
                //                if (encryptedField == null) {
                //                    return encryptedInstance;
                //                }
                //
                //                EncryptedPersonalData encryptedPersonalData;
                //                if (encryptedField instanceof Map) {
                //                    encryptedPersonalData = objectMapper.convertValue(encryptedField, EncryptedPersonalData.class);
                //                } else {
                //                    throw new UnsupportedEncryptedFieldFormatException(
                //                        "Unsupported encrypted field format for field: " + fieldPath
                //                    );
                //                }
                //
                //                String subjectId = subjectIdentifierFinder.findSubjectIdentifier(encryptedInstance, subjectIdentifierFields);
                //
                //                EncryptedData encryptedData = new EncryptedData(
                //                    Base64.getDecoder().decode(encryptedPersonalData.getData()),
                //                    new IvParameterSpec(Base64.getDecoder().decode(encryptedPersonalData.getIv())),
                //                    encryptedPersonalData.getKeyId()
                //                );
                //
                //                byte[] decryptedBytes = decryptor.decrypt(encryptedData, subjectId);
                //                String decryptedValue = new String(decryptedBytes, StandardCharsets.UTF_8);
                //
                //                setFieldValue(objectMap, fieldPath, decryptedValue);
                //
                //                return objectMapper.convertValue(objectMap, (Class<T>) encryptedInstance.getClass());
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt field: " + fieldPath, e);
            }
        });
    }

    @Override
    public ByteBuffer valueFrom(T instance) {
        throw new UnsupportedOperationException("Nested field paths are not supported in this version: " + fieldPath);
    }

    private Object getFieldValue(Map<String, Object> objectMap, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.");
        Object current = objectMap;

        for (String part : pathParts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    private void setFieldValue(Map<String, Object> objectMap, String fieldPath, Object value) {
        String[] pathParts = fieldPath.split("\\.");
        Map<String, Object> current = objectMap;

        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                next = new java.util.HashMap<String, Object>();
                current.put(part, next);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nextMap = (Map<String, Object>) next;
            current = nextMap;
        }

        current.put(pathParts[pathParts.length - 1], value);
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public JsonNode getFieldSchema() {
        return fieldSchema;
    }
}
