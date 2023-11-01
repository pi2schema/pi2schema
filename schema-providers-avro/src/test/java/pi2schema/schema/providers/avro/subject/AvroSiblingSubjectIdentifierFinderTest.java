package pi2schema.schema.providers.avro.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.acme.UserValid;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import pi2schema.schema.providers.avro.personaldata.AvroPersonalDataFieldDefinition;


public class AvroSiblingSubjectIdentifierFinderTest {


    private final AvroSiblingSubjectIdentifierFinder finder = new AvroSiblingSubjectIdentifierFinder();

    @Test
    void shouldFindTheSubjectIdentifierInAValidEvent() {
        String subjectIdentifier = UUID.randomUUID().toString();

        UserValid validUser = UserValid.newBuilder().setUuid(subjectIdentifier)
                .setEmail("john@doe.com")
                .setFavoriteNumber(7).build();

        var emailField = validUser.getSchema().getField("email");


        var definition = finder.find(new AvroPersonalDataFieldDefinition(emailField, validUser.getSchema()));

        var actualSubjectIdentifier = definition.subjectFrom(validUser);

        assertThat(actualSubjectIdentifier).isEqualTo(subjectIdentifier);
    }

    @Test
    void shouldThrowSubjectIdentifierNotFound() {
        fail();
    }


    @Test
    public void shouldThrowTooManySubjectIdentifiers() {
        fail();
    }

}
