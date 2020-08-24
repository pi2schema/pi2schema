package pi2schema.crypto;

public interface Decryptor {

    byte[] decrypt(String key, EncryptedData data);

}
