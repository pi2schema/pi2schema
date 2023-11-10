package pi2schema.crypto.support;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;

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
