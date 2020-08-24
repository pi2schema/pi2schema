package pi2schema.schema.personaldata;

import pi2schema.crypto.Encryptor;

public interface PersonalDataFieldDefinition<T> extends PersonalDataValueProvider<T> {

    void swapToEncrypted(Encryptor encryptor, T buildingInstance);

}
