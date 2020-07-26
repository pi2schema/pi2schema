package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class OneOfEncryptableField {

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = (f) ->
            EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final Descriptors.OneofDescriptor containerOneOfDescriptor;

    public OneOfEncryptableField(Descriptors.OneofDescriptor descriptor) {
        this.containerOneOfDescriptor = descriptor;
    }

    static boolean isEncryptable(@NotNull Descriptors.OneofDescriptor descriptor) {
        //TODO this validation should be on metadata generation
        return descriptor.getFields()
                .stream()
                .anyMatch(isEncryptedFieldType);
    }

    //TODO: make all of this private/internals: expose only one swap to encrypted, swap to unencrypted?
    public Descriptors.OneofDescriptor getContainerOneOfDescriptor() {
        return containerOneOfDescriptor;
    }

    public FieldDescriptor getEncryptedTargetField() {
        Stream<FieldDescriptor> encryptedFieldTypes = containerOneOfDescriptor.getFields()
                .stream()
                .filter(isEncryptedFieldType);


        //TODO on metadageneration, save target, which is immutable for the same metadata, no need to inspect again
        return encryptedFieldTypes.findFirst().orElseThrow(RuntimeException::new);
    }
}
