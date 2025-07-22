package pi2schema.schema.providers.jsonschema.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration test demonstrating end-to-end JSON Schema PII handling.
 */
class JsonSchemaIntegrationTest {

    @Mock
    private Encryptor encryptor;

    @Mock
    private Decryptor decryptor;

    private JsonSchemaPersonalMetadataProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        provider = new JsonSchemaPersonalMetadataProvider();
    }

    @Test
    void shouldEncryptAndDecryptPiiFieldsInJsonObject() {
        // Given: JSON Schema with PII annotations
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "email": {
                  "oneOf": [
                    {
                      "type": "string",
                      "pi2schema-personal-data": true
                    },
                    {
                      "$ref": "#/$defs/EncryptedPersonalData"
                    }
                  ]
                },
                "name": {
                  "type": "string"
                }
              },
              "$defs": {
                "EncryptedPersonalData": {
                  "type": "object",
                  "properties": {
                    "subjectId": {"type": "string"},
                    "data": {"type": "string"},
                    "personalDataFieldNumber": {"type": "string"},
                    "usedTransformation": {"type": "string"},
                    "initializationVector": {"type": "string"},
                    "kmsId": {"type": "string"}
                  }
                }
              }
            }
            """;

        // Mock encryption
        EncryptedData mockEncryptedData = new EncryptedData(
            ByteBuffer.wrap("encrypted_data".getBytes()),
            "AES/GCM/NoPadding",
            new IvParameterSpec("1234567890123456".getBytes())
        );
        when(encryptor.encrypt(eq("user-123"), any(ByteBuffer.class)))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedData));

        // Mock decryption
        ByteBuffer decryptedBuffer = ByteBuffer.wrap("john@example.com".getBytes(StandardCharsets.UTF_8));
        when(decryptor.decrypt(eq("user-123"), any(EncryptedData.class)))
            .thenReturn(CompletableFuture.completedFuture(decryptedBuffer));

        // JSON object with PII data
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", "user-123");
        userData.put("email", "john@example.com");
        userData.put("name", "John Doe");

        // When: Encrypt the data
        var metadata = provider.forSchema(schema);
        var encryptedData = metadata.swapToEncrypted(encryptor, userData);

        // Then: Email should be encrypted, other fields unchanged
        assertThat(encryptedData.get("userId")).isEqualTo("user-123");
        assertThat(encryptedData.get("name")).isEqualTo("John Doe");
        assertThat(encryptedData.get("email")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> encryptedEmail = (Map<String, Object>) encryptedData.get("email");
        assertThat(encryptedEmail.get("subjectId")).isEqualTo("user-123");
        assertThat(encryptedEmail.get("usedTransformation")).isEqualTo("AES/GCM/NoPadding");

        // When: Decrypt the data
        var decryptedData = metadata.swapToDecrypted(decryptor, encryptedData);

        // Then: Original data should be restored
        assertThat(decryptedData.get("userId")).isEqualTo("user-123");
        assertThat(decryptedData.get("email")).isEqualTo("john@example.com");
        assertThat(decryptedData.get("name")).isEqualTo("John Doe");
    }

    @Test
    void shouldHandleNestedPiiFields() {
        // Given: Schema with nested PII field
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "string",
                  "pi2schema-subject-identifier": true
                },
                "user": {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "email": {
                          "type": "string",
                          "pi2schema-personal-data": true
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

        // Mock encryption/decryption
        EncryptedData mockEncryptedData = new EncryptedData(
            ByteBuffer.wrap("encrypted_data".getBytes()),
            "AES/GCM/NoPadding",
            new IvParameterSpec("1234567890123456".getBytes())
        );
        when(encryptor.encrypt(eq("user-123"), any(ByteBuffer.class)))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedData));

        ByteBuffer decryptedBuffer = ByteBuffer.wrap("nested@example.com".getBytes(StandardCharsets.UTF_8));
        when(decryptor.decrypt(eq("user-123"), any(EncryptedData.class)))
            .thenReturn(CompletableFuture.completedFuture(decryptedBuffer));

        // Nested JSON object
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", "user-123");
        userData.put("user", Map.of("profile", Map.of("email", "nested@example.com")));

        // When: Process the data
        var metadata = provider.forSchema(schema);
        var encryptedData = metadata.swapToEncrypted(encryptor, userData);
        var decryptedData = metadata.swapToDecrypted(decryptor, encryptedData);

        // Then: Nested email should be handled correctly
        assertThat(decryptedData.get("userId")).isEqualTo("user-123");

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) decryptedData.get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
        assertThat(profile.get("email")).isEqualTo("nested@example.com");
    }

    @Test
    void shouldNotRequireEncryptionForSchemaWithoutPiiFields() {
        // Given: Schema without PII fields
        String schema =
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
              }
            }
            """;

        // When: Analyze schema
        var metadata = provider.forSchema(schema);

        // Then: No encryption should be required
        assertThat(metadata.requiresEncryption()).isFalse();

        // Given: Simple data object
        Map<String, Object> userData = Map.of("name", "John Doe", "age", 30);

        // When: Process data (should be no-op)
        var processedData = metadata.swapToEncrypted(encryptor, userData);

        // Then: Data should remain unchanged
        assertThat(processedData).isEqualTo(userData);
        assertThat(processedData).isNotSameAs(userData); // Should still create a copy
    }
}
