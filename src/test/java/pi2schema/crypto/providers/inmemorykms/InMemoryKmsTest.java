package pi2schema.crypto.providers.inmemorykms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.EncryptingMaterial;

import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKmsTest {

    private InMemoryKms kms;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        kms = new InMemoryKms();
    }

    @Test
    void encryptionKeysFor() {

        EncryptingMaterial aSubject = kms.encryptionKeysFor("aSubject");

        assertThat(aSubject).isNotNull();
        assertThat(aSubject.getEncryptionKey()).isNotNull();

        EncryptingMaterial sameEncryptingMaterial = kms.encryptionKeysFor("aSubject");
        assertThat(sameEncryptingMaterial)
                .isEqualTo(aSubject);
    }

    @Test
    void decryptionKeysFor() {
        EncryptingMaterial aSubject = kms.encryptionKeysFor("aSubject");

        DecryptingMaterial aSubjectRetrieved = kms.decryptionKeysFor("aSubject");

        assertThat(aSubject).isEqualTo(aSubjectRetrieved);
    }

}