package pi2schema.crypto.tink;

import com.google.crypto.tink.Aead;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

public class TinkEncryptor implements Encryptor {

    private final Aead aead;

    public TinkEncryptor(Aead aead) {
        this.aead = aead;
    }

    @Override
    public CompletableFuture<EncryptedData> encrypt(String subjectId, ByteBuffer data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] plaintext = new byte[data.remaining()];
                data.get(plaintext);
                byte[] ciphertext = aead.encrypt(plaintext, subjectId.getBytes());
                return new EncryptedData(ByteBuffer.wrap(ciphertext));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
