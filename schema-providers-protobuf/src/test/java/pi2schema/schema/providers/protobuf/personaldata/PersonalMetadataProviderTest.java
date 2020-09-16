package pi2schema.schema.providers.protobuf.personaldata;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FruitFixture;
import com.acme.PlantaeOuterClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalMetadataProviderTest {

    PersonalMetadataProvider personalMetadataProvider;

    @BeforeEach
    public void setUp() {
        this.personalMetadataProvider = new PersonalMetadataProvider();
    }

    @Test
    void givenADescriptorWithoutOneOfThenOptionalEmpty() {

        var descriptorWithoutOneOf = FruitFixture.waterMelon().build().getDescriptorForType();

        var metadata = personalMetadataProvider.forDescriptor(descriptorWithoutOneOf);

        assertThat(metadata.requiresEncryption())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithoutPersonalDataSiblingThenOptionalEmpty() {

        var descriptorWithOneOf = PlantaeOuterClass.Plantae.newBuilder()
                .setFruit(FruitFixture.waterMelon())
                .build()
                .getDescriptorForType();

        var metadata = personalMetadataProvider.forDescriptor(descriptorWithOneOf);

        assertThat(metadata.requiresEncryption())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithPersonalDataAsSiblingThenPersonalMetadata() {
        var descriptor = FarmerRegisteredEventFixture.johnDoe()
                .build()
                .getDescriptorForType();

        var metadata = personalMetadataProvider.forDescriptor(descriptor);

        assertThat(metadata.requiresEncryption())
                .isTrue();
    }

    @Test
    void shouldCache() {
//        fail();
    }
}