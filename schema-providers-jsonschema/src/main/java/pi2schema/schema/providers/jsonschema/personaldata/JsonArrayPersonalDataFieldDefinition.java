package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;
import pi2schema.schema.providers.jsonschema.model.EncryptedPersonalData;
import pi2schema.schema.providers.jsonschema.schema.JsonPiiFieldInfo;
import pi2schema.schema.providers.jsonschema.schema.JsonSchemaMetadata;
import pi2schema.schema.providers.jsonschema.subject.JsonSiblingSubjectIdentifierFinder;
import pi2schema.schema.providers.jsonschema.util.JsonObjectUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

/**
 * Handles PII fields that are arrays of personal data.
 * Extends the base field definition to support array operations.
 */
public class JsonArrayPersonalDataFieldDefinition implements PersonalDataFieldDefinition<Map<String, Object>> {

    private final JsonPiiFieldInfo piiFieldInfo;
    private final JsonSchemaMetadata schemaMetadata;
    private final JsonSiblingSubjectIdentifierFinder subjectIdentifierFinder;
    private final ObjectMapper objectMapper;

    public JsonArrayPersonalDataFieldDefinition(JsonPiiFieldInfo piiFieldInfo, JsonSchemaMetadata schemaMetadata) {
        this.piiFieldInfo = piiFieldInfo;
        this.schemaMetadata = schemaMetadata;
        this.subjectIdentifierFinder = new JsonSiblingSubjectIdentifierFinder();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, Map<String, Object> buildingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();
        Object arrayValue = JsonObjectUtils.getNestedValue(buildingInstance, fieldPath);

        if (!(arrayValue instanceof List)) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        List<Object> arrayList = (List<Object>) arrayValue;
        String subjectIdentifier = subjectIdentifierFinder
            .find(new JsonPersonalDataFieldDefinition(piiFieldInfo, schemaMetadata))
            .subjectFrom(buildingInstance);

        List<CompletableFuture<EncryptedPersonalData>> futures = new ArrayList<>();

        for (int i = 0; i < arrayList.size(); i++) {
            Object item = arrayList.get(i);
            if (item != null) {
                String valueAsString = String.valueOf(item);
                final int index = i;

                CompletableFuture<EncryptedPersonalData> future = encryptor
                    .encrypt(subjectIdentifier, ByteBuffer.wrap(valueAsString.getBytes(StandardCharsets.UTF_8)))
                    .thenApply(encrypted -> {
                        // Handle read-only ByteBuffer
                        ByteBuffer dataBuffer = encrypted.data();
                        byte[] dataBytes = new byte[dataBuffer.remaining()];
                        dataBuffer.get(dataBytes);

                        return new EncryptedPersonalData(
                            subjectIdentifier,
                            Base64.getEncoder().encodeToString(dataBytes),
                            String.valueOf(index),
                            encrypted.usedTransformation(),
                            Base64.getEncoder().encodeToString(encrypted.initializationVector().getIV()),
                            "unused-kafkaKms"
                        );
                    });

                futures.add(future);
            } else {
                futures.add(CompletableFuture.completedFuture(null));
            }
        }

        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(ignored -> {
                List<Object> encryptedArray = new ArrayList<>();
                for (CompletableFuture<EncryptedPersonalData> future : futures) {
                    EncryptedPersonalData encryptedData = future.join();
                    if (encryptedData != null) {
                        encryptedArray.add(objectMapper.convertValue(encryptedData, Map.class));
                    } else {
                        encryptedArray.add(null);
                    }
                }
                JsonObjectUtils.setNestedValue(buildingInstance, fieldPath, encryptedArray);
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, Map<String, Object> decryptingInstance) {
        String fieldPath = piiFieldInfo.getFieldPath();
        Object arrayValue = JsonObjectUtils.getNestedValue(decryptingInstance, fieldPath);

        if (!(arrayValue instanceof List)) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        List<Object> arrayList = (List<Object>) arrayValue;
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (Object item : arrayList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> encryptedMap = (Map<String, Object>) item;

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

                    CompletableFuture<String> future = decryptor
                        .decrypt(subjectIdentifier, encryptedData)
                        .thenApply(decryptedData -> {
                            byte[] decryptedBytes = new byte[decryptedData.remaining()];
                            decryptedData.get(decryptedBytes);
                            return new String(decryptedBytes, StandardCharsets.UTF_8);
                        });

                    futures.add(future);
                } catch (Exception e) {
                    futures.add(CompletableFuture.completedFuture(String.valueOf(item)));
                }
            } else {
                futures.add(CompletableFuture.completedFuture(item != null ? String.valueOf(item) : null));
            }
        }

        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(ignored -> {
                List<String> decryptedArray = new ArrayList<>();
                for (CompletableFuture<String> future : futures) {
                    decryptedArray.add(future.join());
                }
                JsonObjectUtils.setNestedValue(decryptingInstance, fieldPath, decryptedArray);
            });
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
}
