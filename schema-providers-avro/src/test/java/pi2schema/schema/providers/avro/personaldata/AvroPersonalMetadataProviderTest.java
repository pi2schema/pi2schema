package pi2schema.schema.providers.avro.personaldata;

import com.acme.InvalidUserWithMultipleSubjectIdentifiers;
import com.acme.InvalidUserWithoutSubjectIdentifier;
import com.acme.UserValid;
import org.apache.avro.specific.SpecificRecordBase;
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
        var without = InvalidUserWithoutSubjectIdentifier.newBuilder().build();
        var metadata = personalMetadataProvider.forType(without);

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithUnionWithoutPersonalDataSiblingThenNoEncryptionRequired() {


        var doubleIdentifiers = InvalidUserWithMultipleSubjectIdentifiers.newBuilder().build();
        var metadata = personalMetadataProvider.forType(doubleIdentifiers);

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithPersonalDataAsSiblingInUnionType() {
        var valid = UserValid.newBuilder().build();
        var metadata = personalMetadataProvider.forType(valid);

        assertThat(metadata.requiresEncryption()).isTrue();
    }

    @Test
    void shouldCache() {
        //        fail();
    }
}
