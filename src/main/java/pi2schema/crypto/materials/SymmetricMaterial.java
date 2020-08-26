package pi2schema.crypto.materials;

import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.util.Objects;


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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymmetricMaterial that = (SymmetricMaterial) o;
        return cryptoKey.equals(that.cryptoKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cryptoKey);
    }
}
