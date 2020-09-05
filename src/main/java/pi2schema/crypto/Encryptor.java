package pi2schema.crypto;

import java.util.concurrent.CompletableFuture;

public interface Encryptor {

    CompletableFuture<EncryptedData> encrypt(String subjectId, byte[] data);
}
