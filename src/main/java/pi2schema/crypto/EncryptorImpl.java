package pi2schema.crypto;

import pi2schema.crypto.providers.EncryptingMaterialsProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Default implementation performing the actual encryption / decryption using the standard Java building blocks for cryptography.
 * <p>
 * Relies on {@link EncryptingMaterialsProvider} for the Secret retrieval and creation used for the cryptography.
 */
public class EncryptorImpl implements Encryptor {

    // https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher
    // Might consider extracting these values somewhere else or make it configurable
    private static final String MODE = "CBC"; //avoiding default aes ECB
    private static final String PADDING = "PKCS5Padding";

    private final EncryptingMaterialsProvider provider;

    public EncryptorImpl(EncryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public EncryptedData encrypt(String subjectId, byte[] data) {
        SecretKey encryptionKey = provider.encryptionKeysFor(subjectId).getEncryptionKey();

        try {

            String transformation = String.format("%s/%s/%s", encryptionKey.getAlgorithm(), MODE, PADDING);
            Cipher cipher = Cipher.getInstance(transformation);

            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
            cipher.update(data);

            return new EncryptedData(
                    cipher.doFinal(),
                    transformation,
                    new IvParameterSpec(cipher.getIV())
            );

        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | BadPaddingException
                | IllegalBlockSizeException
                | InvalidKeyException e) {
            throw new RuntimeException(e); //todo wrap internal
        }
    }

}
