package pi2schema.crypto.materials;

import javax.crypto.SecretKey;

public class SymmetricMaterial implements EncryptingMaterial, DecryptingMaterial {

    private final SecretKey cryptoKey;
    private final String subjectId;

    public SymmetricMaterial(SecretKey encryptionKey, String subjectId) {
        this.cryptoKey = encryptionKey;
        this.subjectId = subjectId;
    }

    @Override
    public SecretKey getEncryptionKey() {
        return cryptoKey;
    }

    @Override
    public SecretKey getDecryptionKey() {
        return cryptoKey;
    }

    @Override
    public String subjectId() {
        return subjectId;
    }
}
