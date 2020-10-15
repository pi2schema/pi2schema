# Pi2schema protobuf support

 The idea about pi2schema project pop up during protobuf experimentations, specially because of the great flexibility
  and metadata introspection provided by it.
 
 This section presents how to adjust the proto schema to describe the personal data.
 
 
> :warning: **Draft api**: Most likely the following definitions will change until version 1.0.

> :ear: **Feedback is welcome**: Please give your feedback [here](https://github.com/pi2schema/pi2schema/issues/new).

> :info: **Example available**: The [examples/spring-boot-protobuf-kafka](../examples/springboot-protobuf-kafkakms) provides a simple implementation for the covered points bellow.

## Subject Identifier
 
 The subject identification is made by adding to the protobuf field the annotation  **[(pi2schema.subject_identifier
 ) = true]**
 
> :waring: Currently the subject identifier is saved as plain. This work fine with uuid or auto incremental data
>, however should be reviewed case the identifier itself is a personal data, for instance the email. It should be
> straight forward to improve this part. Please create an issue if this is important for you use case.

 The current implementation is quite simple and there is room for extension for example to determine the subject
 identifier from the message key or message headers. Please create a [github issue](https://github.com/pi2schema/pi2schema/issues/new)
 if this is important for your use case.
 
## Personal data
 
 To describe a protobuf field as a field which contains personal data which should be encrypted during serialization
  and possible decrypted during the deserialization one must:
   1. Surround the personal data field with an *oneOf* protobuf feature.
   2. Add a pi2schema.EncryptedPersonalData field to the oneOf container.
 
 There may exist multiple personal data fields in the oneOf container however must exist only one pi2schema
 .EncryptedPersonalData. The deserialization will decrypt and transform the data to the right business target field.
 
 The usage of a protobuf oneOf container adds great flexibility by using a clear EncryptedPersonalData contract/field
  without tricking the schema and populating its data with bytecodes which does not represent the schema and its
   original fields. Still, feedback is more than welcome for this approach. 