package pi2schema.crypto.support;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;

public final class KeyGen {
    public static final KeyGenerator aes256() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator;
    }
}
