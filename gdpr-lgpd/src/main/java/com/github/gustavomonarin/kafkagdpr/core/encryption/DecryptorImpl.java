package com.github.gustavomonarin.kafkagdpr.core.encryption;

import com.github.gustavomonarin.kafkagdpr.core.encryption.providers.DecryptingMaterialsProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

public class DecryptorImpl implements Decryptor {

    private DecryptingMaterialsProvider provider;

    public DecryptorImpl(DecryptingMaterialsProvider provider) {
        this.provider = provider;
    }

    @Override
    public byte[] decrypt(String key, EncryptedData encryptedData) {

        try {
            SecretKey decryptionKey = provider.decryptionKeysFor(key).getDecryptionKey();

            Cipher cipher = Cipher.getInstance(encryptedData.usedTransformation());

            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, encryptedData.initializationVector());

            return cipher.doFinal(encryptedData.data());

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e); // todo wrap internal exception
        }

    }
}
