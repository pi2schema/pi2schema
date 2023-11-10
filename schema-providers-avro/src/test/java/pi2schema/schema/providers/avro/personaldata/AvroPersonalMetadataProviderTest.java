package pi2schema.schema.providers.avro.personaldata;

import com.acme.InvalidUserWithMultipleSubjectIdentifiers;
import com.acme.InvalidUserWithoutSubjectIdentifier;
import com.acme.UserValid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroPersonalMetadataProviderTest {

    AvroPersonalMetadataProvider personalMetadataProvider;

    @BeforeEach
    public void setUp() {
        this.personalMetadataProvider = new AvroPersonalMetadataProvider();
    }

    @Test
    void givenADescriptorWithoutOneOfThenOptionalEmpty() {
        var metadata = personalMetadataProvider.forDescriptor(InvalidUserWithoutSubjectIdentifier.getClassSchema());

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithUnionWithoutPersonalDataSiblingThenNoEncryptionRequired() {
        var metadata = personalMetadataProvider.forDescriptor(
            InvalidUserWithMultipleSubjectIdentifiers.getClassSchema()
        );

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithPersonalDataAsSiblingInUnionType() {
        var metadata = personalMetadataProvider.forDescriptor(UserValid.getClassSchema());

        assertThat(metadata.requiresEncryption()).isTrue();
    }

    @Test
    void shouldCache() {
        //        fail();
    }
}
