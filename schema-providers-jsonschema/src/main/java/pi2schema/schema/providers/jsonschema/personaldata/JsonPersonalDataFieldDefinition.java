package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.PropertyUtils;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.InvalidEncryptedMessageException;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;
import pi2schema.schema.providers.jsonschema.model.EncryptedPersonalData;
import pi2schema.schema.providers.jsonschema.subject.JsonSubjectIdentifierFieldDefinition;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

/**
 * Implementation of PersonalDataFieldDefinition for JSON Schema.
 * Handles encryption and decryption of PII fields in business objects.
 * Simplified to work directly with field path and schema information.
 */
public class JsonPersonalDataFieldDefinition<T> implements PersonalDataFieldDefinition<T> {

    public static final String PERSONAL_DATA_EXTENSION = "pi2schema-personal-data";

    private final String fieldPath;
    private final JsonNode fieldSchema;
    private final JsonSubjectIdentifierFieldDefinition jsonSubjectIdentifierFieldDefinition;
    private final ObjectMapper objectMapper;

    public JsonPersonalDataFieldDefinition(
        String fieldPath,
        JsonSubjectIdentifierFieldDefinition jsonSubjectIdentifierFieldDefinition
    ) {
        this.fieldPath = fieldPath;
        this.fieldSchema = null;
        this.jsonSubjectIdentifierFieldDefinition = jsonSubjectIdentifierFieldDefinition;
        objectMapper = new ObjectMapper();
    }

    static boolean hasPersonalData(JsonNode field) {
        return field.get(PERSONAL_DATA_EXTENSION) != null && field.get(PERSONAL_DATA_EXTENSION).asBoolean();
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, T encryptingInstance) {
        var personalData = valueFrom(encryptingInstance);
        if (personalData == null || !personalData.hasRemaining()) { //nothing to encrypt
            return CompletableFuture.allOf();
        }

        var subjectId = jsonSubjectIdentifierFieldDefinition.subjectFrom(encryptingInstance);

        return encryptor
            .encrypt(subjectId, personalData)
            .thenAccept(encrypted -> {
                try {
                    PropertyUtils.setProperty(
                        encryptingInstance,
                        fieldPath,
                        toEncryptedPersonalDataJson(subjectId, encrypted)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, T encryptedInstance) {
        try {
            Object encryptedField = PropertyUtils.getProperty(encryptedInstance, fieldPath);
            if (encryptedField == null) {
                return CompletableFuture.allOf(); // nothing to decrypt
            }

            var encryptedPersonalData = objectMapper.readValue((String) encryptedField, EncryptedPersonalData.class);
            var encryptedData = toEncryptedData(encryptedPersonalData);

            return decryptor
                .decrypt(encryptedPersonalData.getSubjectId(), encryptedData)
                .thenAccept(decryptedBytes -> {
                    String decryptedValue = new String(decryptedBytes.array(), StandardCharsets.UTF_8);
                    try {
                        PropertyUtils.setProperty(encryptedInstance, fieldPath, decryptedValue);
                    } catch (Exception e) {
                        throw new InvalidEncryptedMessageException(e);
                    }
                });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public ByteBuffer valueFrom(T instance) {
        try {
            var property = PropertyUtils.getProperty(instance, fieldPath);
            if (property instanceof String s) {
                return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        throw new UnsupportedOperationException("The type of the field %s is not supported".formatted(fieldPath));
    }

    private String toEncryptedPersonalDataJson(String subjectId, EncryptedData encryptedData) {
        EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
            subjectId,
            encryptedData.data().array(),
            fieldPath,
            encryptedData.usedTransformation(),
            new String(encryptedData.initializationVector().getIV()),
            null
        );
        try {
            return objectMapper.writeValueAsString(encryptedPersonalData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private EncryptedData toEncryptedData(EncryptedPersonalData encryptedPersonalData) {
        return new EncryptedData(
            ByteBuffer.wrap(encryptedPersonalData.getData()).asReadOnlyBuffer(),
            encryptedPersonalData.getUsedTransformation(),
            new IvParameterSpec(encryptedPersonalData.getInitializationVector().getBytes(StandardCharsets.UTF_8))
        );
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public JsonNode getFieldSchema() {
        return fieldSchema;
    }
}
