## Running
There is a docker compose with all the needed tooling including the newer schema registry with protobuf support, first of all one has to start it.

Most of the code is implemented on simple junit tests, publishing and consuming the messages

## Repo structure
The branches master and v1-initial-events have the initial implementation with a couple of events. 
The branch v2-new-event-added has a second implementation in order to simulate a different consumer and producer.

Switching between the branches should allow us to simulate multiple consumers and producers in a isolated enough way without much code  duplication.

## Avro union limitation

Avro has many positive design decisions however when trying to add multiple events from the same aggregate in the same topic to avoid unnecessary reordering complexity using union types, the following [limitation](http://apache-avro.679487.n3.nabble.com/add-a-type-to-a-union-td4033581.html) can be considered a blocker

 `
Union types are powerful, but you must take care when changing them. If you want to add a type to a union, you first need to update all readers with the new schema, so that they know what to expect. Only once all readers are updated, the writers may start putting this new type in the records they generate.
 `  [ref](https://martin.kleppmann.com/2012/12/05/schema-evolution-in-avro-protocol-buffers-thrift.html) 
    
Once an initial version of the schema is published and used by the consumers extending the union type with new types will give the following error:

```
Caused by: org.apache.avro.AvroTypeException: Found **NewType**, expecting union
	at org.apache.avro.io.ResolvingDecoder.doAction(ResolvingDecoder.java:308)
```

The suggested solution for this case is to replace the unique union field by multiple nullable fields for each possible type. Although this solution works for the consumer the code becomes unnatural and the event confusing. 

### Protobuf in the confluent schema support

Protobuf has some interesting alternatives to the union type and this repo intends to go beyond its definition and test how it works in practice and its implementation using java.

#### oneOf:
 *If you have a message with many fields and where at most one field will be set at the same time, you can enforce this behavior and save memory by using the oneof feature.*. Which is exactly the previously suggested approach to union types, with the difference that the concept is clear from the schema definition, and the protobuf generator is going to take care to enforce the behavior. 

```protobuf
oneof event {
       Harvested harvested = 4;
       RetailerReceived retailerReceived = 5;
     };
``` 
[src/main/proto/evenlop_using_oneof.proto]

The code for the consumer for handle the message in java is still not natural, but the base building blocks are there:
```java
switch (message.getEventCase()) {
    case HARVESTED -> log("Handling typed harvested ev %s", message.getHarvested());
    case RETAILERRECEIVED -> log("Handling typed retailer received ev %s", message.getRetailerReceived());
    default -> log("Ignoring unknown event %s", message);
}
```

#### google.protobuf.Any:
*The Any message type lets you use messages as embedded types without having their .proto definition.* Which is a bit more flexible and certainly has its usage in more flexible structures however when putting domain events in the center of the solution, a bit more of constraints makes things more clear IMHO.

The proto definition is quite simple as the Any is the type of the message. For handling this would be the code in java:
```java
if (event.is(EnvelopAnyOuterClass.Harvested.class)) {
    log("Handling typed harvested ev %s", event.unpack(EnvelopAnyOuterClass.Harvested.class));
} else if (event.is(EnvelopAnyOuterClass.RetailerReceived.class)) {
    log("Handling typed retailer received ev %s", event.unpack(EnvelopAnyOuterClass.RetailerReceived.class));
} else {
    log("Ignoring unknown event %s" + event);
}
```

#### google.protobuf.Struct:
*Any JSON object. See `struct.proto`.* Will stop here :), although Istio has some interesting work around it and apparently in the clojure world it seems quite natural. more?

### Build system and gradle support
Even the build system  scenario has changed quite a lot. 

A couple of years, a bit more, gradle support for avro and schema registry was quite limited compared to the available plugins for Maven and its well-defined lifecycle.  As of 2020, maven still seems to have a more mature ecosystem including the official confluent schema plugins  however one can easy find third party plugins  for the serialization also for the schema registry. This repository experiments in both including schema validation. [TODO]