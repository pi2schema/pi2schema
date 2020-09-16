package pi2schema.crypto;

import pi2schema.functional.ThrowingConsumer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

class Ciphers implements Supplier<Cipher> {
    private final String transformation;
    private final ThrowingConsumer<Cipher, GeneralSecurityException> init;

    private Ciphers(String transformation, ThrowingConsumer<Cipher, GeneralSecurityException> init) {
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

    static Ciphers forEncryption(SecretKey secretKey, String transformation) {
        return new Ciphers(transformation, cipher ->
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        );
    }

    static Ciphers forDecryption(SecretKey secretKey, EncryptedData encryptedData) {
        return new Ciphers(encryptedData.usedTransformation(), cipher ->
                cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedData.initializationVector())
        );
    }

    static CompletableFuture<byte[]> apply(Cipher cipher, byte[] bytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return cipher.doFinal(bytes);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
