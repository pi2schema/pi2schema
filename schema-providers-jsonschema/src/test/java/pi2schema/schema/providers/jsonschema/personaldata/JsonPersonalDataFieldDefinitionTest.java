package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.providers.jsonschema.subject.JsonSubjectIdentifierFieldDefinition;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JsonPersonalDataFieldDefinition covering encryption/decryption operations
 * and error handling scenarios (AC-003, AC-004, AC-007).
 */
class JsonPersonalDataFieldDefinitionTest {

    @Mock
    private Encryptor encryptor;

    @Mock
    private Decryptor decryptor;

    @Mock
    private JsonSubjectIdentifierFieldDefinition subjectIdentifierField;

    private JsonPersonalDataFieldDefinition<Map<String, Object>> fieldDefinition;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fieldDefinition = new JsonPersonalDataFieldDefinition<>("email", subjectIdentifierField);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldEncryptPiiField() throws Exception {
        // Given: A business object with PII data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "john@example.com");

        // Mock the subject identifier extraction
        when(subjectIdentifierField.subjectFrom(businessObject)).thenReturn("user-123");

        // Mock encryption response
        EncryptedData mockEncryptedData = new EncryptedData(
            ByteBuffer.wrap("encrypted_data".getBytes()),
            "AES/GCM/NoPadding",
            new IvParameterSpec("1234567890123456".getBytes())
        );
        when(encryptor.encrypt(eq("user-123"), any(ByteBuffer.class)))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedData));

        // When: Encrypt the field
        var result = fieldDefinition.swapToEncrypted(encryptor, businessObject);
        result.join(); // Wait for completion

        // Then: The email field should be replaced with encrypted structure
        assertThat(businessObject.get("userId")).isEqualTo("user-123"); // Non-PII unchanged
        assertThat(businessObject.get("email")).isInstanceOf(String.class); // Should be JSON string now

        // Parse the encrypted field to verify structure
        String encryptedJson = (String) businessObject.get("email");
        var encryptedObject = objectMapper.readTree(encryptedJson);
        assertThat(encryptedObject.get("subjectId").asText()).isEqualTo("user-123");
        assertThat(encryptedObject.get("usedTransformation").asText()).isEqualTo("AES/GCM/NoPadding");
        assertThat(encryptedObject.has("data")).isTrue();
        assertThat(encryptedObject.has("initializationVector")).isTrue();
    }

    @Test
    void shouldDecryptPiiField() throws Exception {
        // Given: A business object with encrypted PII data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");

        // Create encrypted JSON structure
        String encryptedJson =
            """
            {
              "subjectId": "user-123",
              "data": "ZW5jcnlwdGVkX2RhdGE=",
              "personalDataFieldNumber": "email",
              "usedTransformation": "AES/GCM/NoPadding",
              "initializationVector": "MTIzNDU2Nzg5MDEyMzQ1Ng=="
            }
            """;
        businessObject.put("email", encryptedJson);

        // Mock decryption response
        ByteBuffer decryptedData = ByteBuffer.wrap("john@example.com".getBytes(StandardCharsets.UTF_8));
        when(decryptor.decrypt(eq("user-123"), any(EncryptedData.class)))
            .thenReturn(CompletableFuture.completedFuture(decryptedData));

        // When: Decrypt the field
        var result = fieldDefinition.swapToDecrypted(decryptor, businessObject);
        result.join(); // Wait for completion

        // Then: The original plaintext value should be restored
        assertThat(businessObject.get("userId")).isEqualTo("user-123"); // Non-PII unchanged
        assertThat(businessObject.get("email")).isEqualTo("john@example.com"); // Decrypted
    }

    @Test
    void shouldHandleNullFieldDuringEncryption() {
        // Given: A business object with null PII field
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", null);

        // When: Attempt to encrypt
        var result = fieldDefinition.swapToEncrypted(encryptor, businessObject);
        result.join(); // Wait for completion

        // Then: No encryption should occur, field remains null
        assertThat(businessObject.get("email")).isNull();
    }

    @Test
    void shouldHandleEmptyFieldDuringEncryption() {
        // Given: A business object with empty PII field
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "");

        // When: Attempt to encrypt
        var result = fieldDefinition.swapToEncrypted(encryptor, businessObject);
        result.join(); // Wait for completion

        // Then: No encryption should occur, field remains empty
        assertThat(businessObject.get("email")).isEqualTo("");
    }

    @Test
    void shouldHandleNullFieldDuringDecryption() {
        // Given: A business object with null encrypted field
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", null);

        // When: Attempt to decrypt
        var result = fieldDefinition.swapToDecrypted(decryptor, businessObject);
        result.join(); // Wait for completion

        // Then: No decryption should occur, field remains null
        assertThat(businessObject.get("email")).isNull();
    }

    @Test
    void shouldThrowExceptionForMalformedEncryptedData() {
        // Given: A business object with malformed encrypted data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "invalid-json-data");

        // When/Then: Attempting to decrypt should throw an exception (AC-007)
        var result = fieldDefinition.swapToDecrypted(decryptor, businessObject);

        assertThrows(Exception.class, () -> result.join());
    }

    @Test
    void shouldExtractValueFromBusinessObject() {
        // Given: A business object with PII data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("email", "john@example.com");

        // When: Extract value
        ByteBuffer result = fieldDefinition.valueFrom(businessObject);

        // Then: Should return the value as ByteBuffer
        String extractedValue = new String(result.array(), StandardCharsets.UTF_8);
        assertThat(extractedValue).isEqualTo("john@example.com");
    }

    @Test
    void shouldReturnFieldPath() {
        // When/Then: Field path should be accessible
        assertThat(fieldDefinition.getFieldPath()).isEqualTo("email");
    }

    @Test
    void shouldHandleEncryptionFailure() {
        // Given: A business object with PII data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");
        businessObject.put("email", "john@example.com");

        when(subjectIdentifierField.subjectFrom(businessObject)).thenReturn("user-123");

        // Mock encryption failure
        when(encryptor.encrypt(eq("user-123"), any(ByteBuffer.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Encryption failed")));

        // When/Then: Should handle encryption failure gracefully
        var result = fieldDefinition.swapToEncrypted(encryptor, businessObject);

        assertThrows(Exception.class, () -> result.join());
    }

    @Test
    void shouldHandleDecryptionFailure() {
        // Given: A business object with encrypted data
        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("userId", "user-123");

        String encryptedJson =
            """
            {
              "subjectId": "user-123",
              "data": "ZW5jcnlwdGVkX2RhdGE=",
              "personalDataFieldNumber": "email",
              "usedTransformation": "AES/GCM/NoPadding",
              "initializationVector": "MTIzNDU2Nzg5MDEyMzQ1Ng=="
            }
            """;
        businessObject.put("email", encryptedJson);

        // Mock decryption failure
        when(decryptor.decrypt(eq("user-123"), any(EncryptedData.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Decryption failed")));

        // When/Then: Should handle decryption failure gracefully
        var result = fieldDefinition.swapToDecrypted(decryptor, businessObject);

        assertThrows(Exception.class, () -> result.join());
    }
}
