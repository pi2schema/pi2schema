Experiments around schemas and schema evolution specially protobuf and its new schema support in the confluent schema registry.

### Background
The opportunity to experiment / review schema decisions made in the past are unfortunately rare in the day to day work.

This repo intends to review some limitations faced in the past with avro schema specially when using it with multiple domain events from the same aggregate in the same topic to avoid unnecessary reordering complexity. 

On the other hand also would like to review the extreme flexibility  faced using pure CloudEvents + json with lose or no  schema at all.

For a more detailed comparison  on the schema evolution and domain evolution comparing avro and protobuf, see the [sub-project readme](cp-schema-registry-protobuf-avro/README.md)

Going beyond the previously faced problems, while starting a new project (specially personal project), one has new challenges as GDPR, ids/urns, documentation as code which can be seen as opportunity to do it right from the design.

Please feel free to reach me out by mail / twitter / issues in case of any suggestions as well as way to push me to publish more in this shared repo regarding the other points that are only superficially mentioned
 (This repo intends to double validate/discuss with the community my approach. There is a separated repo with a personal project which in some areas already have further development).


### Next
* GDPR, LGPD schema following the privacy by design principle, from the beginning with a clear contract and transparent for impl
* Urn schema for resource identification as of pcalcado 
* Event Modeling [Adam Dymitruk](https://www.eventmodeling.org) as a code / executable specification
* Recent developments in the CloudEvents specification, including [Re-Introducing protobuf](https://github.com/cloudevents/spec/pull/626)