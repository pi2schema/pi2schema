package pi2schema.schema.providers.avro.personaldata;

import com.acme.UserValid;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import pi2schema.EncryptedPersonalData;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class AvroUnionPersonalDataFieldDefinitionTest {

    @Test
    void swapToEncrypted() {

    }

    @Test
    void swapToEncryptedShouldIgnoreEncryptionWhenPersonalDataIsNotSet() {

    }

    @Test
    void swapToDecryted() throws ExecutionException, InterruptedException {
        //given
        var uuid = UUID.randomUUID().toString();
        var encrypted = ByteBuffer.wrap("encryptedMocked".getBytes());
        var decrypted = "john.doe@email.com";

        EncryptedPersonalData encryptedPersonalData = EncryptedPersonalData.newBuilder()
                .setSubjectId(uuid)
                .setData(encrypted)
                .setPersonalDataFieldNumber(0)
                .setKmsId("unused")
                .setUsedTransformation("unused")
                .setInitializationVector(ByteBuffer.wrap("unused".getBytes()))
                .build();


        var validUser = UserValid.newBuilder().setUuid(uuid)
                .setEmail(encryptedPersonalData)
                .setFavoriteNumber(5)
                .build();

        Decryptor decryptor = (key, data) -> {
            if (key.equals(uuid) && getDataAsString(data).equals("encryptedMocked"))
                return CompletableFuture.completedFuture(ByteBuffer.wrap(decrypted.getBytes()));
            throw new IllegalArgumentException();
        };

        Schema.Field encryptedField = new Schema.Field("email", UserValid.SCHEMA$);

        //when
        AvroUnionPersonalDataFieldDefinition avroUnionPersonalDataFieldDefinition = new AvroUnionPersonalDataFieldDefinition(encryptedField, UserValid.SCHEMA$);
        avroUnionPersonalDataFieldDefinition.swapToDecrypted(decryptor, validUser).get();

        //then
        assertThat(validUser.getEmail()).isEqualTo("john.doe@email.com");
    }


    @Test
    void shouldThrowEncryptionTargetFieldNotFoundException() {

    }

    @Test
    void shouldThrowTooManyEncryptionTargetFieldException() {

    }

    private static String getDataAsString(EncryptedData data) {
        byte[] dataBytes = new byte[data.data().remaining()];
        data.data().get(dataBytes);
        return new String(dataBytes);
    }
}