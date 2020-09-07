package pi2schema.schema.personaldata;

import pi2schema.crypto.Encryptor;

import java.util.concurrent.CompletableFuture;

public interface PersonalDataFieldDefinition<T> extends PersonalDataValueProvider<T> {

    CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, T buildingInstance);

}
