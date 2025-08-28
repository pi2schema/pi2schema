package pi2schema.schema.providers.avro.personaldata;

import com.acme.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroPersonalMetadataProviderTest {

    AvroPersonalMetadataProvider<InvalidUserWithoutSubjectIdentifier> personalMetadataProvider;

    @BeforeEach
    public void setUp() {
        this.personalMetadataProvider = new AvroPersonalMetadataProvider<>();
    }

    @Test
    void givenADescriptorWithoutOneOfThenOptionalEmpty() {
        var without = InvalidUserWithoutSubjectIdentifierFixture.johnDoe().build();
        var metadata = personalMetadataProvider.forSchema(without.getSchema());

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithUnionWithoutPersonalDataSiblingThenNoEncryptionRequired() {
        var doubleIdentifiers = InvalidUserWithMultipleSubjectIdentifiersFixture.johnDoe().build();
        var metadata = personalMetadataProvider.forSchema(doubleIdentifiers.getSchema());

        assertThat(metadata.requiresEncryption()).isFalse();
    }

    @Test
    void givenADescriptorWithPersonalDataAsSiblingInUnionType() {
        var valid = UserValidFixture.johnDoe().build();
        var metadata = personalMetadataProvider.forSchema(valid.getSchema());

        assertThat(metadata.requiresEncryption()).isTrue();
    }

    @Test
    void shouldCache() {
        //        fail();
    }
}
