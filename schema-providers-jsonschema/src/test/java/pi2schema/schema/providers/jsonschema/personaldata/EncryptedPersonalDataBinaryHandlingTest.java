package pi2schema.schema.providers.jsonschema.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.EncryptedData;
import pi2schema.schema.providers.jsonschema.model.EncryptedPersonalData;

import java.nio.ByteBuffer;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedPersonalDataBinaryHandlingTest {

    @Test
    void shouldProperlyHandleBase64EncodingOfBinaryData() throws Exception {
        byte[] originalData = "This is some encrypted test data".getBytes();

        String dataBase64 = Base64.getEncoder().encodeToString(originalData);

        EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
            "test-subject",
            dataBase64,
            "test-field",
            "AES/CBC/PKCS5Padding",
            "",
            "test-kms"
        );

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(encryptedPersonalData);
        EncryptedPersonalData deserialized = objectMapper.readValue(json, EncryptedPersonalData.class);

        assertThat(deserialized.getData()).isEqualTo(dataBase64);

        byte[] decodedData = Base64.getDecoder().decode(deserialized.getData());

        assertThat(decodedData).isEqualTo(originalData);
    }

    @Test
    void shouldHandleRandomBinaryDataSafely() {
        byte[] randomData = new byte[] { -1, -2, -3, 0, 1, 2, 3, (byte) 0xFF, (byte) 0xFE, (byte) 0x80 };

        String dataBase64 = Base64.getEncoder().encodeToString(randomData);

        EncryptedPersonalData encryptedPersonalData = new EncryptedPersonalData(
            "test-subject",
            dataBase64,
            "test-field",
            "AES/CBC/PKCS5Padding",
            "",
            null
        );

        byte[] decodedData = Base64.getDecoder().decode(encryptedPersonalData.getData());

        assertThat(decodedData).isEqualTo(randomData);
    }

    @Test
    void shouldHandleEncryptedDataConversionCorrectly() {
        byte[] data = "encrypted content".getBytes();

        EncryptedData encryptedData = new EncryptedData(
            ByteBuffer.wrap(data).asReadOnlyBuffer()
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
        } catch (Exception e) {
            throw new RuntimeException("Test failed due to reflection issues", e);
        }
    }
}
