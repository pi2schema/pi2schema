package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.EncryptedData;
import pi2schema.schema.providers.jsonschema.model.EncryptedPersonalData;

import java.nio.ByteBuffer;
import java.util.Base64;

import javax.crypto.spec.IvParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedPersonalDataBinaryHandlingTest {

    @Test
    void shouldProperlyHandleBase64EncodingOfBinaryData() throws Exception {
        byte[] originalData = "This is some encrypted test data".getBytes();
        byte[] originalIv = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };

        String dataBase64 = Base64.getEncoder().encodeToString(originalData);
        String ivBase64 = Base64.getEncoder().encodeToString(originalIv);

        EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
            "test-subject",
            dataBase64,
            "test-field",
            "AES/CBC/PKCS5Padding",
            ivBase64,
            "test-kms"
        );

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(encryptedPersonalData);
        EncryptedPersonalData deserialized = objectMapper.readValue(json, EncryptedPersonalData.class);

        assertThat(deserialized.getData()).isEqualTo(dataBase64);
        assertThat(deserialized.getInitializationVector()).isEqualTo(ivBase64);

        byte[] decodedData = Base64.getDecoder().decode(deserialized.getData());
        byte[] decodedIv = Base64.getDecoder().decode(deserialized.getInitializationVector());

        assertThat(decodedData).isEqualTo(originalData);
        assertThat(decodedIv).isEqualTo(originalIv);
    }

    @Test
    void shouldHandleRandomBinaryDataSafely() {
        byte[] randomData = new byte[] { -1, -2, -3, 0, 1, 2, 3, (byte) 0xFF, (byte) 0xFE, (byte) 0x80 };
        byte[] randomIv = new byte[] {
            (byte) 0x80,
            (byte) 0x81,
            (byte) 0x82,
            (byte) 0x83,
            (byte) 0x84,
            (byte) 0x85,
            (byte) 0x86,
            (byte) 0x87,
            (byte) 0x88,
            (byte) 0x89,
            (byte) 0x8A,
            (byte) 0x8B,
            (byte) 0x8C,
            (byte) 0x8D,
            (byte) 0x8E,
            (byte) 0x8F,
        };

        String dataBase64 = Base64.getEncoder().encodeToString(randomData);
        String ivBase64 = Base64.getEncoder().encodeToString(randomIv);

        EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
            "test-subject",
            dataBase64,
            "test-field",
            "AES/CBC/PKCS5Padding",
            ivBase64,
            null
        );

        byte[] decodedData = Base64.getDecoder().decode(encryptedPersonalData.getData());
        byte[] decodedIv = Base64.getDecoder().decode(encryptedPersonalData.getInitializationVector());

        assertThat(decodedData).isEqualTo(randomData);
        assertThat(decodedIv).isEqualTo(randomIv);
    }

    @Test
    void shouldHandleEncryptedDataConversionCorrectly() {
        byte[] data = "encrypted content".getBytes();
        byte[] iv = new byte[16]; // Standard AES block size
        // Fill with some test values
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) i;
        }

        EncryptedData encryptedData = new EncryptedData(
            ByteBuffer.wrap(data).asReadOnlyBuffer(),
            "AES/CBC/PKCS5Padding",
            new IvParameterSpec(iv)
        );

        JsonPersonalDataFieldDefinition<Object> fieldDefinition = new JsonPersonalDataFieldDefinition<>(
            "testField",
            null
        );

        // Use reflection to access private method for testing
        try {
            var method =
                JsonPersonalDataFieldDefinition.class.getDeclaredMethod(
                        "toEncryptedPersonalDataJson",
                        String.class,
                        EncryptedData.class
                    );
            method.setAccessible(true);
            String json = (String) method.invoke(fieldDefinition, "test-subject", encryptedData);

            ObjectMapper objectMapper = new ObjectMapper();
            EncryptedPersonalData parsed = objectMapper.readValue(json, EncryptedPersonalData.class);

            var toEncryptedDataMethod =
                JsonPersonalDataFieldDefinition.class.getDeclaredMethod("toEncryptedData", EncryptedPersonalData.class);
            toEncryptedDataMethod.setAccessible(true);
            EncryptedData converted = (EncryptedData) toEncryptedDataMethod.invoke(fieldDefinition, parsed);

            byte[] convertedData = new byte[converted.data().remaining()];
            converted.data().get(convertedData);

            assertThat(convertedData).isEqualTo(data);
            assertThat(converted.initializationVector().getIV()).isEqualTo(iv);
            assertThat(converted.usedTransformation()).isEqualTo("AES/CBC/PKCS5Padding");
        } catch (Exception e) {
            throw new RuntimeException("Test failed due to reflection issues", e);
        }
    }
}
