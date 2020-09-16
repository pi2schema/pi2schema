package pi2schema.crypto;

import javax.crypto.spec.IvParameterSpec;

public final class EncryptedData {

    private final byte[] data;
    private final String usedTransformation;
    private final IvParameterSpec initializationVector;

    public EncryptedData(byte[] data, String usedTransformation, IvParameterSpec initializationVector) {
        this.data = data;
        this.usedTransformation = usedTransformation;
        this.initializationVector = initializationVector;
    }

    /**
     * @return The encrypted/ciphered data
     */
    public byte[] data() {
        return data;
    }

    /**
     * Transformation following the structure: {algo}/{mode}/{padding}
     * For more details:
     *
     * <a href=https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher>possible values</a>
     *
     * @return The used transformation
     */
    public String usedTransformation() {
        return usedTransformation;
    }

    /**
     * @return The used initialization vector in the data
     */
    public IvParameterSpec initializationVector() {
        return initializationVector;
    }
}
