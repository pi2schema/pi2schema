package pi2schema.crypto;

import pi2schema.functional.ThrowingConsumer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.function.Supplier;

class CipherSupplier implements Supplier<Cipher> {
    private final String transformation;
    private ThrowingConsumer<Cipher, GeneralSecurityException> init;

    private CipherSupplier(String transformation, ThrowingConsumer<Cipher, GeneralSecurityException> init) {
        this.transformation = transformation;
        this.init = init;
    }

    @Override
    public Cipher get() {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            init.accept(cipher);
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static CipherSupplier forEncryption(SecretKey secretKey, String transformation) {
        return new CipherSupplier(transformation, cipher ->
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        );
    }

    static CipherSupplier forDecryption(SecretKey secretKey, EncryptedData encryptedData) {
        return new CipherSupplier(encryptedData.usedTransformation(), cipher ->
                cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedData.initializationVector())
        );
    }
}
