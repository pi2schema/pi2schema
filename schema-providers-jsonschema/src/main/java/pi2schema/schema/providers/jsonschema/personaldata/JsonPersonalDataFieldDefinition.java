package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;
import pi2schema.schema.personaldata.UnsupportedEncryptedFieldFormatException;
import pi2schema.schema.providers.jsonschema.model.EncryptedPersonalData;
import pi2schema.schema.providers.jsonschema.schema.JsonPiiFieldInfo;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;
import pi2schema.schema.providers.jsonschema.subject.JsonSiblingSubjectIdentifierFinder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

/**
 * Implementation of PersonalDataFieldDefinition for JSON Schema.
 * Handles encryption and decryption of PII fields in business objects.
 *
 * NOTE: This version supports simple top-level field access only.
 * Nested field support will be added in future versions.
 */
public class JsonPersonalDataFieldDefinition<T> implements PersonalDataFieldDefinition<T> {

    private final JsonPiiFieldInfo piiFieldInfo;
    private final JsonSchemaMetadata schemaMetadata;
    private final ObjectMapper objectMapper;
    private final JsonSiblingSubjectIdentifierFinder<T> subjectIdentifierFinder;

    public JsonPersonalDataFieldDefinition(JsonPiiFieldInfo piiFieldInfo, JsonSchemaMetadata schemaMetadata) {
        this.piiFieldInfo = piiFieldInfo;
        this.schemaMetadata = schemaMetadata;
        this.subjectIdentifierFinder = new JsonSiblingSubjectIdentifierFinder<>();
        this.objectMapper = new ObjectMapper();
    }

    public JsonPersonalDataFieldDefinition(
        JsonPiiFieldInfo piiFieldInfo,
        JsonSchemaMetadata schemaMetadata,
        ObjectMapper objectMapper
    ) {
        this.piiFieldInfo = piiFieldInfo;
        this.schemaMetadata = schemaMetadata;
        this.subjectIdentifierFinder = new JsonSiblingSubjectIdentifierFinder<>();
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, T buildingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();

        // Simple field access - only top-level fields supported
        if (fieldPath.contains(".")) {
            throw new UnsupportedOperationException(
                "Nested field paths are not supported in this version: " + fieldPath
            );
        }

        // Convert business object to Map for field access
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = objectMapper.convertValue(buildingInstance, Map.class);

        Object decryptedValue = objectMap.get(fieldPath);

        if (decryptedValue == null) {
            // Field is optional and not present
            return CompletableFuture.completedFuture(null);
        }

        String subjectIdentifier = subjectIdentifierFinder
            .find(schemaMetadata, piiFieldInfo.getFieldPath())
            .subjectFrom(buildingInstance);
        String valueAsString = String.valueOf(decryptedValue);

        return encryptor
            .encrypt(subjectIdentifier, ByteBuffer.wrap(valueAsString.getBytes(StandardCharsets.UTF_8)))
            .thenAccept(encrypted -> {
                // Handle read-only ByteBuffer by creating a copy
                ByteBuffer dataBuffer = encrypted.data();
                byte[] dataBytes = new byte[dataBuffer.remaining()];
                dataBuffer.get(dataBytes);

                EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
                    subjectIdentifier,
                    Base64.getEncoder().encodeToString(dataBytes),
                    "0", // field number - simplified for JSON
                    encrypted.usedTransformation(),
                    Base64.getEncoder().encodeToString(encrypted.initializationVector().getIV()),
                    "unused-kafkaKms" // TODO: make configurable
                );

                // Update the field in the map representation
                objectMap.put(fieldPath, objectMapper.convertValue(encryptedPersonalData, Map.class));

                // Update the original business object with the modified field
                updateBusinessObjectField(
                    buildingInstance,
                    fieldPath,
                    objectMapper.convertValue(encryptedPersonalData, Map.class)
                );
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, T decryptingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();

        // Simple field access - only top-level fields supported
        if (fieldPath.contains(".")) {
            throw new UnsupportedOperationException(
                "Nested field paths are not supported in this version: " + fieldPath
            );
        }

        // Convert business object to Map for field access
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = objectMapper.convertValue(decryptingInstance, Map.class);

        Object encryptedValue = objectMap.get(fieldPath);

        if (encryptedValue == null) {
            // Field is optional and not present
            return CompletableFuture.completedFuture(null);
        }

        if (!(encryptedValue instanceof Map)) {
            throw new UnsupportedEncryptedFieldFormatException(
                EncryptedPersonalData.class.getName(),
                fieldPath,
                encryptedValue.getClass()
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> encryptedMap = (Map<String, Object>) encryptedValue;

        try {
            EncryptedPersonalData encryptedPersonalData = objectMapper.convertValue(
                encryptedMap,
                EncryptedPersonalData.class
            );

            String subjectIdentifier = encryptedPersonalData.getSubjectId();
            byte[] encryptedDataBytes = Base64.getDecoder().decode(encryptedPersonalData.getData());
            byte[] ivBytes = Base64.getDecoder().decode(encryptedPersonalData.getInitializationVector());

            EncryptedData encryptedData = new EncryptedData(
                ByteBuffer.wrap(encryptedDataBytes),
                encryptedPersonalData.getUsedTransformation(),
                new IvParameterSpec(ivBytes)
            );

            return decryptor
                .decrypt(subjectIdentifier, encryptedData)
                .thenAccept(decryptedData -> {
                    // Handle read-only ByteBuffer by creating a copy
                    byte[] decryptedBytes = new byte[decryptedData.remaining()];
                    decryptedData.get(decryptedBytes);
                    String decryptedValue = new String(decryptedBytes, StandardCharsets.UTF_8);

                    // Update the field in the map representation
                    objectMap.put(fieldPath, decryptedValue);

                    // Update the original business object with the modified field
                    updateBusinessObjectField(decryptingInstance, fieldPath, decryptedValue);
                });
        } catch (Exception e) {
            throw new UnsupportedEncryptedFieldFormatException(
                EncryptedPersonalData.class.getName(),
                fieldPath,
                encryptedValue.getClass()
            );
        }
    }

    @Override
    public ByteBuffer valueFrom(T instance) {
        String fieldPath = piiFieldInfo.getFieldPath();

        // Simple field access - only top-level fields supported
        if (fieldPath.contains(".")) {
            throw new UnsupportedOperationException(
                "Nested field paths are not supported in this version: " + fieldPath
            );
        }

        // Convert business object to Map for field access
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = objectMapper.convertValue(instance, Map.class);

        Object value = objectMap.get(fieldPath);
        if (value == null) {
            return null;
        }
        return ByteBuffer.wrap(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }

    public String getFieldPath() {
        return piiFieldInfo.getFieldPath();
    }

    public JsonPiiFieldInfo getPiiFieldInfo() {
        return piiFieldInfo;
    }

    public JsonSchemaMetadata getSchemaMetadata() {
        return schemaMetadata;
    }

    /**
     * Checks if a field has personal data annotations based on JSON Schema extensions.
     */
    public static boolean hasPersonalData(JsonPiiFieldInfo fieldInfo) {
        // This field was already identified as PII during schema analysis
        return true;
    }

    /**
     * Updates a field in the business object with a new value.
     * Handles Maps directly and uses reflection for other object types.
     */
    @SuppressWarnings("unchecked")
    private void updateBusinessObjectField(T businessObject, String fieldPath, Object newValue) {
        // For Maps, we can update directly
        if (businessObject instanceof Map) {
            ((Map<String, Object>) businessObject).put(fieldPath, newValue);
            return;
        }

        // For other business objects, we need to use reflection since Jackson doesn't provide
        // a direct way to update specific fields. This is a limitation of the current approach.
        try {
            Class<?> clazz = businessObject.getClass();
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldPath);
            field.setAccessible(true);
            field.set(businessObject, newValue);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to update field '" +
                fieldPath +
                "' in business object of type " +
                businessObject.getClass().getSimpleName() +
                ". Consider using Map<String, Object> for dynamic field updates or ensure fields have proper setters.",
                e
            );
        }
    }
}
