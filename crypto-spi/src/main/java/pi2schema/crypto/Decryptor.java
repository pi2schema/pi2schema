package pi2schema.crypto;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface Decryptor {
    CompletableFuture<ByteBuffer> decrypt(EncryptedData data);
}
