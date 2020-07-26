package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FruitFixture;
import com.acme.PlantaeOuterClass;
import com.google.protobuf.Descriptors.Descriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class PersonalMetadataProviderTest {

    PersonalMetadataProvider personalMetadataProvider;

    @BeforeEach
    public void setUp() {
        this.personalMetadataProvider = new PersonalMetadataProvider();
    }

    @Test
    void givenADescriptorWithoutOneOfThenOptionalEmpty() {

        Descriptor descriptorWithoutOneOf = FruitFixture.waterMelon().build().getDescriptorForType();

        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(descriptorWithoutOneOf);

        assertThat(metadata.containsEncryptableFields())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithoutPersonalDataSiblingThenOptionalEmpty() {

        Descriptor descriptorWithOneOf = PlantaeOuterClass.Plantae.newBuilder()
                .setFruit(FruitFixture.waterMelon())
                .build()
                .getDescriptorForType();

        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(descriptorWithOneOf);

        assertThat(metadata.containsEncryptableFields())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithPersonalDataAsSiblingThenPersonalMetadata() {
        Descriptor descriptor = FarmerRegisteredEventFixture.johnDoe()
                .build()
                .getDescriptorForType();

        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(descriptor);

        assertThat(metadata.containsEncryptableFields())
                .isTrue();
    }

    @Test
    void shouldCache() {
        fail();
    }
}