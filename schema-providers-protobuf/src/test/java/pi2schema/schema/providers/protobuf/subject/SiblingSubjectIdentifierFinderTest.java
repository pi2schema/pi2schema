package pi2schema.schema.providers.protobuf.subject;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.InvalidSubjectIdentifiers;
import org.junit.jupiter.api.Test;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SiblingSubjectIdentifierFinderTest {

    private final SiblingSubjectIdentifierFinder finder = new SiblingSubjectIdentifierFinder();

    @Test
    void shouldFindTheSubjectIdentifierInAValidEvent() {
        var johnDoeRegistration = FarmerRegisteredEventFixture.johnDoe();
        var oneOfDescriptor = johnDoeRegistration.getDescriptorForType().getOneofs().get(0);

        var subjectIdentifierFieldDefinition = finder.find(oneOfDescriptor);

        assertThat(subjectIdentifierFieldDefinition).isNotNull();
        assertThat(subjectIdentifierFieldDefinition.subjectFrom(johnDoeRegistration))
            .isEqualTo(johnDoeRegistration.getUuid());
    }

    @Test
    void shouldThrowSubjectIdentifierNotFound() {
        var personalDataField = InvalidSubjectIdentifiers.MissingSubjectIdentifierAnnotation
            .getDescriptor()
            .getOneofs()
            .get(0);

        assertThatExceptionOfType(SubjectIdentifierNotFoundException.class)
            .isThrownBy(() -> finder.find(personalDataField))
            .withMessage(
                "The strategy pi2schema.schema.providers.protobuf.subject.SiblingSubjectIdentifierFinder has not found any possible identifiers for the field" +
                " com.acme.MissingSubjectIdentifierAnnotation.personal_data while exact one is required"
            );
    }

    @Test
    public void shouldThrowTooManySubjectIdentifiers() {
        var personalDataField = InvalidSubjectIdentifiers.MultipleSiblingsSubjectIdentifiers
            .getDescriptor()
            .getOneofs()
            .get(0);

        assertThatExceptionOfType(TooManySubjectIdentifiersException.class)
            .isThrownBy(() -> finder.find(personalDataField))
            .withMessage(
                "The strategy SiblingSubjectIdentifierFinder has found 2 possible identifiers for the field" +
                " com.acme.MultipleSiblingsSubjectIdentifiers.personal_data while exact one is required"
            );
    }
}
