package pi2schema.crypto;

public interface Encryptor {


    EncryptedData encrypt(String subjectId, byte[] data);


}
