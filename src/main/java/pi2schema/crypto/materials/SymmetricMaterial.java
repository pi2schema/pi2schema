package pi2schema.crypto.materials;

import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;


public class SymmetricMaterial implements EncryptingMaterial, DecryptingMaterial {
    private final SecretKey cryptoKey;

    public SymmetricMaterial(@NotNull SecretKey encryptionKey) {
        this.cryptoKey = encryptionKey;
    }

    @Override
    public SecretKey getEncryptionKey() {
        return cryptoKey;
    }

    @Override
    public SecretKey getDecryptionKey() {
        return cryptoKey;
    }

}
