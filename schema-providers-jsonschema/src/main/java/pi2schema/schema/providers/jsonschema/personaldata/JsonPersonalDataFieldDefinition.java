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
import pi2schema.schema.providers.jsonschema.util.JsonObjectUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

/**
 * Implementation of PersonalDataFieldDefinition for JSON Schema.
 * Handles encryption and decryption of PII fields in JSON objects.
 */
public class JsonPersonalDataFieldDefinition implements PersonalDataFieldDefinition<Map<String, Object>> {

    private final JsonPiiFieldInfo piiFieldInfo;
    private final JsonSchemaMetadata schemaMetadata;
    private final JsonSiblingSubjectIdentifierFinder subjectIdentifierFinder;
    private final ObjectMapper objectMapper;

    public JsonPersonalDataFieldDefinition(JsonPiiFieldInfo piiFieldInfo, JsonSchemaMetadata schemaMetadata) {
        this.piiFieldInfo = piiFieldInfo;
        this.schemaMetadata = schemaMetadata;
        this.subjectIdentifierFinder = new JsonSiblingSubjectIdentifierFinder();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, Map<String, Object> buildingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();
        Object decryptedValue = JsonObjectUtils.getNestedValue(buildingInstance, fieldPath);

        if (decryptedValue == null) {
            // Field is optional and not present
            return CompletableFuture.completedFuture(null);
        }

        String subjectIdentifier = subjectIdentifierFinder.find(this).subjectFrom(buildingInstance);
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

                JsonObjectUtils.setNestedValue(
                    buildingInstance,
                    fieldPath,
                    objectMapper.convertValue(encryptedPersonalData, Map.class)
                );
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, Map<String, Object> decryptingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();
        Object encryptedValue = JsonObjectUtils.getNestedValue(decryptingInstance, fieldPath);

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
                    JsonObjectUtils.setNestedValue(decryptingInstance, fieldPath, decryptedValue);
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
    public ByteBuffer valueFrom(Map<String, Object> instance) {
        Object value = JsonObjectUtils.getNestedValue(instance, piiFieldInfo.getFieldPath());
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
}
