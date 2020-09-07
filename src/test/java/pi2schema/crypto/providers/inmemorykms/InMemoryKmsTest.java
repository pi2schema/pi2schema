package pi2schema.crypto.providers.inmemorykms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.EncryptingMaterial;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKmsTest {

    private InMemoryKms kms;

    @BeforeEach
    void setUp() {
        kms = new InMemoryKms();
    }

    @Test
    void encryptionKeysFor() {
        EncryptingMaterial aSubject = kms.encryptionKeysFor("aSubject").join();

        assertThat(aSubject).isNotNull();
        assertThat(aSubject.getEncryptionKey()).isNotNull();

        EncryptingMaterial sameEncryptingMaterial = kms.encryptionKeysFor("aSubject").join();
        assertThat(sameEncryptingMaterial).isEqualTo(aSubject);
    }

    @Test
    void decryptionKeysFor() {
        EncryptingMaterial aSubject = kms.encryptionKeysFor("aSubject").join();
        DecryptingMaterial aSubjectRetrieved = kms.decryptionKeysFor("aSubject").join();

        assertThat(aSubject).isEqualTo(aSubjectRetrieved);
    }
}