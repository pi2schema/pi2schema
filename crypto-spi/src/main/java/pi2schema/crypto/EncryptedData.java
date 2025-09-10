package pi2schema.crypto;

import java.nio.ByteBuffer;

public final class EncryptedData {

    private final ByteBuffer data;

    public EncryptedData(ByteBuffer data) {
        this.data = data.asReadOnlyBuffer();
    }

    /**
     * @return The encrypted/ciphered data
     */
    public ByteBuffer data() {
        return data.asReadOnlyBuffer();
    }
}
