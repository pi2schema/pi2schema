package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FruitFixture;
import com.acme.PlantaeOuterClass;
import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadata;
import com.github.gustavomonarin.kafkagdpr.protobuf.personaldata.PersonalMetadataProvider;
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

        assertThat(metadata.requiresEncryption())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithoutPersonalDataSiblingThenOptionalEmpty() {

        Descriptor descriptorWithOneOf = PlantaeOuterClass.Plantae.newBuilder()
                .setFruit(FruitFixture.waterMelon())
                .build()
                .getDescriptorForType();

        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(descriptorWithOneOf);

        assertThat(metadata.requiresEncryption())
                .isFalse();
    }

    @Test
    void givenADescriptorWithOneOfWithPersonalDataAsSiblingThenPersonalMetadata() {
        Descriptor descriptor = FarmerRegisteredEventFixture.johnDoe()
                .build()
                .getDescriptorForType();

        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(descriptor);

        assertThat(metadata.requiresEncryption())
                .isTrue();
    }

    @Test
    void shouldCache() {
        fail();
    }
}