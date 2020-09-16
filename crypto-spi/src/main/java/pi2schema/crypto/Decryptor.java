package pi2schema.crypto;

import java.util.concurrent.CompletableFuture;

public interface Decryptor {

    CompletableFuture<byte[]> decrypt(String key, EncryptedData data);
}
