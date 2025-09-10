package pi2schema.crypto.tink;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadFactory;
import pi2schema.crypto.CryptoAlgorithm;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.EncryptingMaterial;

import java.security.GeneralSecurityException;

public class TinkAesGcm implements CryptoAlgorithm {

    private final KeysetHandle keysetHandle;

    public TinkAesGcm(KeysetHandle keysetHandle) {
        this.keysetHandle = keysetHandle;
    }

    @Override
    public Encryptor buildEncryptor(EncryptingMaterial material) {
        try {
            Aead aead = AeadFactory.getPrimitive(keysetHandle);
            return new TinkEncryptor(aead);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Decryptor buildDecryptor(DecryptingMaterial material) {
        try {
            Aead aead = AeadFactory.getPrimitive(keysetHandle);
            return new TinkDecryptor(aead, material.subjectId());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
