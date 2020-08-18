package com.github.gustavomonarin.kafkagdpr.protobuf.subject;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.InvalidSubjectIdentifiers;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.github.gustavomonarin.kafkagdpr.core.subject.SubjectIdentifierNotFoundException;
import com.github.gustavomonarin.kafkagdpr.core.subject.TooManySubjectIdentifiersException;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SiblingSubjectIdentifierFinderTest {

    private final SiblingSubjectIdentifierFinder finder = new SiblingSubjectIdentifierFinder();

    @Test
    void shouldFindTheSubjectIdentifierInAValidEvent() {
        FarmerRegisteredEvent.Builder johnDoeRegistration = FarmerRegisteredEventFixture.johnDoe();
        Descriptors.OneofDescriptor oneOfDescriptor = johnDoeRegistration
                .getDescriptorForType()
                .getOneofs()
                .get(0);

        ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition = finder.find(oneOfDescriptor);

        assertThat(subjectIdentifierFieldDefinition).isNotNull();
        assertThat(subjectIdentifierFieldDefinition.subjectFrom(johnDoeRegistration))
                .isEqualTo(johnDoeRegistration.getUuid());
    }

    @Test
    void shouldThrowSubjectIdentifierNotFound() {
        Descriptors.OneofDescriptor personalDataField = InvalidSubjectIdentifiers.MissingSubjectIdentifierAnnotation
                .getDescriptor()
                .getOneofs().get(0);

        assertThatExceptionOfType(SubjectIdentifierNotFoundException.class)
                .isThrownBy(() ->
                        finder.find(personalDataField)
                )
                .withMessage("The strategy SiblingSubjectIdentifierFinder has not found any possible identifiers for the field" +
                        " com.acme.MissingSubjectIdentifierAnnotation.personal_data while exact one is required");

    }

    @Test
    public void shouldThrowTooManySubjectIdentifiers() {
        Descriptors.OneofDescriptor personalDataField = InvalidSubjectIdentifiers.MultipleSiblingsSubjectIdentifiers
                .getDescriptor()
                .getOneofs()
                .get(0);

        assertThatExceptionOfType(TooManySubjectIdentifiersException.class)
                .isThrownBy(() ->
                        finder.find(personalDataField)
                )
                .withMessage("The strategy SiblingSubjectIdentifierFinder has found 2 possible identifiers for the field" +
                        " com.acme.MultipleSiblingsSubjectIdentifiers.personal_data while exact one is required");
    }
}