package pi2schema.crypto;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface Encryptor {
    CompletableFuture<EncryptedData> encrypt(String subjectId, ByteBuffer data);
}
