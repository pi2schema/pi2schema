package pi2schema.crypto;

import org.junit.jupiter.api.Test;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCryptoTest {

    @Test
    void encrypt() throws NoSuchAlgorithmException, ExecutionException, InterruptedException {

        //given
        byte[] toBeEncrypted = "toBeEncrypted".getBytes();

        SecretKey secretKey = KeyGen.aes256().generateKey();

        EncryptingMaterialsProvider encProvider = (s) -> new SymmetricMaterial(secretKey);
        DecryptingMaterialsProvider decProvider = (s) -> new SymmetricMaterial(secretKey);

        Encryptor encryptor = new LocalEncryptor(encProvider);
        Decryptor decryptor = new LocalDecryptor(decProvider);

        //When
        EncryptedData encrypted = encryptor.encrypt("", toBeEncrypted).get();
        byte[] decrypted = decryptor.decrypt("", encrypted);

        //Then
        assertThat(toBeEncrypted).isNotEqualTo(encrypted.data());

        assertThat(decrypted).isEqualTo(toBeEncrypted);
    }
}