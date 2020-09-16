package pi2schema.crypto.support;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;

public final class KeyGen {

    public static KeyGenerator aes256() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            return keyGenerator;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // wrap internal?
        }
    }

}
