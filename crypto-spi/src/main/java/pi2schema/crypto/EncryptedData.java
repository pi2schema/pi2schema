package pi2schema.crypto;

import java.nio.ByteBuffer;

public record EncryptedData(ByteBuffer data, ByteBuffer encryptedDataKey, String keysetHandle) {
    public EncryptedData(ByteBuffer data, ByteBuffer encryptedDataKey, String keysetHandle) {
        this.data = data.asReadOnlyBuffer();
        this.encryptedDataKey = encryptedDataKey.asReadOnlyBuffer();
        this.keysetHandle = keysetHandle;
    }

    /**
     * @return The encrypted/ciphered data
     */
    @Override
    public ByteBuffer data() {
        return data.asReadOnlyBuffer();
    }

    /**
     * @return The encrypted data encryption key (DEK) for envelope cryptography
     */
    @Override
    public ByteBuffer encryptedDataKey() {
        return encryptedDataKey.asReadOnlyBuffer();
    }

    /**
     * @return The keyset handle identifier used for encryption
     */
    @Override
    public String keysetHandle() {
        return keysetHandle;
    }
}
