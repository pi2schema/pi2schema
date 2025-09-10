package pi2schema.schema.providers.avro.personaldata;

import com.acme.UserValid;
import org.junit.jupiter.api.Test;
import pi2schema.EncryptedPersonalData;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class AvroUnionPersonalDataFieldDefinitionTest {

    @Test
    void swapToEncrypted() throws ExecutionException, InterruptedException {
        var uuid = UUID.randomUUID().toString();
        var decrypted = "john.doe@email.com";
        var encrypted = ByteBuffer.wrap("encryptedMocked".getBytes());

        var validUser = UserValid.newBuilder().setUuid(uuid).setEmail(decrypted).setFavoriteNumber(5).build();

        Encryptor encryptor = (key, data) -> {
            if (key.equals(uuid) && getByteBufferAsString(data).equals("john.doe@email.com")) {
                var encryptedData = new EncryptedData(
                    encrypted
                );
                return CompletableFuture.completedFuture(encryptedData);
            }
            throw new IllegalArgumentException();
        };

        var decryptedField = new org.apache.avro.Schema.Field("email", UserValid.SCHEMA$);

        //when
        AvroUnionPersonalDataFieldDefinition avroUnionPersonalDataFieldDefinition =
            new AvroUnionPersonalDataFieldDefinition(decryptedField, UserValid.SCHEMA$);
        avroUnionPersonalDataFieldDefinition.swapToEncrypted(encryptor, validUser).get();

        //then
        EncryptedPersonalData email = (EncryptedPersonalData) validUser.getEmail();
        assertThat(email.getData()).isEqualTo(encrypted);
    }

    @Test
    void swapToEncryptedShouldIgnoreEncryptionWhenPersonalDataIsNotSet() {}

    @Test
    void swapToDecryted() throws ExecutionException, InterruptedException {
        //given
        var uuid = UUID.randomUUID().toString();
        var encrypted = ByteBuffer.wrap("encryptedMocked".getBytes());
        var decrypted = "john.doe@email.com";

        EncryptedPersonalData encryptedPersonalData = EncryptedPersonalData
            .newBuilder()
            .setSubjectId(uuid)
            .setData(encrypted)
            .setPersonalDataFieldNumber("0")
            .setKmsId("unused")
            .setUsedTransformation("unused")
            .setInitializationVector(ByteBuffer.wrap("unused".getBytes()))
            .build();

        var validUser = UserValid
            .newBuilder()
            .setUuid(uuid)
            .setEmail(encryptedPersonalData)
            .setFavoriteNumber(5)
            .build();

        Decryptor decryptor = (data) -> {
            if (getByteBufferAsString(data.data()).equals("encryptedMocked")) {
                return CompletableFuture.completedFuture(ByteBuffer.wrap(decrypted.getBytes()));
            }
            throw new IllegalArgumentException();
        };

        var encryptedField = new org.apache.avro.Schema.Field("email", UserValid.SCHEMA$);

        //when
        AvroUnionPersonalDataFieldDefinition avroUnionPersonalDataFieldDefinition =
            new AvroUnionPersonalDataFieldDefinition(encryptedField, UserValid.SCHEMA$);
        avroUnionPersonalDataFieldDefinition.swapToDecrypted(decryptor, validUser).get();

        //then
        assertThat(validUser.getEmail()).isEqualTo("john.doe@email.com");
    }

    @Test
    void shouldThrowEncryptionTargetFieldNotFoundException() {}

    @Test
    void shouldThrowTooManyEncryptionTargetFieldException() {}

    private static String getByteBufferAsString(ByteBuffer byteBuffer) {
        byte[] dataBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(dataBytes);
        byteBuffer.rewind(); // Added to allow reading the buffer again
        return new String(dataBytes);
    }
}
