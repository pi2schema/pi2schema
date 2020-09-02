package pi2schema.crypto.providers.inmemorykms;

import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.EncryptingMaterial;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Development focused in memory key management.
 * Should not be considered to something else than tests as the keys are not accessible between the nodes.
 */
public class InMemoryKms implements EncryptingMaterialsProvider, DecryptingMaterialsProvider {

    private final KeyGenerator keyGenerator;

    private final Map<String, SymmetricMaterial> keyStore = new HashMap<>();

    public InMemoryKms() throws NoSuchAlgorithmException {
        this(KeyGenerator.getInstance("AES"));
        this.keyGenerator.init(256);
    }

    public InMemoryKms(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    @Override
    public EncryptingMaterial encryptionKeysFor(@NotNull String subjectId) {
        return keyStore.computeIfAbsent(subjectId, missingKey ->
                new SymmetricMaterial(keyGenerator.generateKey()));
    }

    @Override
    public DecryptingMaterial decryptionKeysFor(@NotNull String subjectId) {
        return keyStore.get(subjectId);
    }
}
