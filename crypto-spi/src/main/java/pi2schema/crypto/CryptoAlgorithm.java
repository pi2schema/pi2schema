package pi2schema.crypto;

import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.EncryptingMaterial;

public interface CryptoAlgorithm {
    Encryptor buildEncryptor(EncryptingMaterial material);
    Decryptor buildDecryptor(DecryptingMaterial material);
}
