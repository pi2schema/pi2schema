package pi2schema.crypto.materials;

import javax.crypto.SecretKey;

public interface EncryptingMaterial {

    SecretKey getEncryptionKey();

}
