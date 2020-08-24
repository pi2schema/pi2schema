package pi2schema.schema.providers.protobuf.personaldata;

import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PersonalMetadata {

    private final List<OneOfPersonalDataFieldDefinition> personalDataFields;

    public PersonalMetadata(@NotNull List<OneOfPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    public void encryptPersonalData(Encryptor encryptor, Message.Builder encryptingBuilder) {

        personalDataFields.forEach(field ->
                field.swapToEncrypted(encryptor, encryptingBuilder)
        );
    }

    public void decryptPersonalData(Decryptor decryptor, Message.Builder decryptingBuilder){

        personalDataFields.forEach(field ->
                field.swapToDecrypted(decryptor, decryptingBuilder));

    }
}
