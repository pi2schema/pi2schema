package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.schema.personaldata.PersonalMetadata;
import pi2schema.schema.personaldata.PersonalMetadataProvider;

import java.util.Collections;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class AvroPersonalMetadataProvider<T extends SpecificRecordBase> implements PersonalMetadataProvider<T, Schema> {

    @Override
    public PersonalMetadata<T> forSchema(Schema schema) {
        //avro union strategy
        var personalDataFieldDefinitions = schema
            .getFields()
            .stream()
            .filter(AvroUnionPersonalDataFieldDefinition::hasPersonalData)
            .map(f -> new AvroUnionPersonalDataFieldDefinition(f, schema))
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        return new AvroPersonalMetadata<>(personalDataFieldDefinitions);
    }
}
