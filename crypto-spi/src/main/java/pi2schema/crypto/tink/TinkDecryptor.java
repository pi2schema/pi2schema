package pi2schema.crypto.tink;

import com.google.crypto.tink.Aead;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

public class TinkDecryptor implements Decryptor {

    private final Aead aead;
    private final String subjectId;

    public TinkDecryptor(Aead aead, String subjectId) {
        this.aead = aead;
        this.subjectId = subjectId;
    }

    @Override
    public CompletableFuture<ByteBuffer> decrypt(EncryptedData data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] ciphertext = new byte[data.data().remaining()];
                data.data().get(ciphertext);
                byte[] decrypted = aead.decrypt(ciphertext, subjectId.getBytes());
                return ByteBuffer.wrap(decrypted);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
