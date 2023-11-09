package pi2schema.schema.providers.avro.subject;

import com.acme.InvalidUserWithMultipleSubjectIdentifiers;
import com.acme.InvalidUserWithoutSubjectIdentifier;
import com.acme.UserValid;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pi2schema.schema.providers.avro.personaldata.AvroUnionPersonalDataFieldDefinition;
import pi2schema.schema.subject.SubjectIdentifierNotFoundException;
import pi2schema.schema.subject.TooManySubjectIdentifiersException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class AvroSiblingSubjectIdentifierFinderTest {

    private final AvroSiblingSubjectIdentifierFinder finder = new AvroSiblingSubjectIdentifierFinder();

    @Test
    void shouldFindTheSubjectIdentifierInAValidEvent() {
        String subjectIdentifier = UUID.randomUUID().toString();

        UserValid validUser = UserValid
            .newBuilder()
            .setUuid(subjectIdentifier)
            .setEmail("john@doe.com")
            .setFavoriteNumber(7)
            .build();

        var emailField = validUser.getSchema().getField("email");

        var definition = finder.find(new AvroUnionPersonalDataFieldDefinition(emailField, validUser.getSchema()));

        var actualSubjectIdentifier = definition.subjectFrom(validUser);

        assertThat(actualSubjectIdentifier).isEqualTo(subjectIdentifier);
    }

    @Test
    void shouldThrowSubjectIdentifierNotFound() {
        String subjectIdentifier = UUID.randomUUID().toString();
        InvalidUserWithoutSubjectIdentifier validUser = InvalidUserWithoutSubjectIdentifier
            .newBuilder()
            .setUuid(subjectIdentifier)
            .setEmail("john@doe.com")
            .setFavoriteNumber(7)
            .build();
        var emailField = validUser.getSchema().getField("email");

        assertThrowsExactly(
            SubjectIdentifierNotFoundException.class,
            () -> finder.find(new AvroUnionPersonalDataFieldDefinition(emailField, validUser.getSchema()))
        );
    }

    @Test
    public void shouldThrowTooManySubjectIdentifiers() {
        String subjectIdentifier = UUID.randomUUID().toString();
        InvalidUserWithMultipleSubjectIdentifiers validUser = InvalidUserWithMultipleSubjectIdentifiers
            .newBuilder()
            .setUuid(subjectIdentifier)
            .setEmail("john@doe.com")
            .setFavoriteNumber(7)
            .build();
        var emailField = validUser.getSchema().getField("email");

        assertThrowsExactly(
            TooManySubjectIdentifiersException.class,
            () -> finder.find(new AvroUnionPersonalDataFieldDefinition(emailField, validUser.getSchema()))
        );
    }

    @Test
    @Disabled
    public void shouldThrowNotStringTypeSubjectIdentifiers() {}
}
