# Sample application 

[![asciicast](https://asciinema.org/a/9f7BENd3T6cGj0kuxdEmYJ2R2.png)](https://asciinema.org/a/9f7BENd3T6cGj0kuxdEmYJ2R2?speed=3)


# First a normal application
For purposes of demonstration, the application is developed/designed without the personal data concepts at first.

There is a tag [springboot-protobuf-kafka-kms-before](tree/springboot-protobuf-kafka-kms-before) for this state.

It uses the apache kafka and google protobuf for its long term data durability.

The project contains two sub-components within the same codebase and toggled by simple spring profiles, for
 simplicity reasons.

# Components
* Onboarding: Rest api and publishes FarmerRegistered Event (profiles: onboarding, onboarding-auto-publish)
* Newsletter: Listens to farmer registered events and sends weekly newsletter emails (profiles: newsletter)

# Adding pi2schema

## Gradle Dependency
Currently, by adding the dependency `pi2schema:serialization-kafka-protobuf:0.1.0` will bring all the required
  dependencies, which are _schema metadata_, _key manamement system_ and _serializer_
In the current sample, as it is a subproject it is just:

```groovy build.grade
	compile(project(":serialization-kafka-protobuf"))
```

## Schema definition - PII metadata annotation.

> :warning: **Draft api**: Most likely the following definitions will change until version 1.0

> :ear: *Feedback is welcome*. Please give your feedback [here](https://github.com/pi2schema/pi2schema/issues/new).
 
The schema must define the Subject Identifier as well as which part of the payload contains personal data. 

To identify the Subject Identifier we add the *[(pi2schema.subject_identifier) = true]* annotation to the uuid field:
```protobuf
string uuid = 1[(pi2schema.subject_identifier) = true];
```

For defining a personal data field we wrap it with an oneOf tag and simply add a new element 
*pi2schema.EncryptedPersonalData encryptedPersonalData = 6;* as part of the oneOf. The library will identify this
 pattern and do the replacement on the serialization / deserialization.
 
 ```protobuf
  oneof personalData {
    ContactInfo contactInfo = 2;
    pi2schema.EncryptedPersonalData encryptedPersonalData = 6;
  }
```

## Key management

> :warning: Not safe for production. Please for the time being consider integrating a 3rd party kms as aws kms, gcp
> kms or Hashicorp Vault


The key management that is currently used out of the box is a simple JCE based AES-256 local encryptor and decryptor
. The secret key is stored in a kafka topic for durability.


## Running the services

Producer:
```shell
./gradlew examples:springboot-avro-kafkakms:bootRun --args='--spring.profiles.active=geoLocation'
```

Listener:
```shell
./gradlew examples:springboot-avro-kafkakms:bootRun --args='--spring.profiles.active=preProcessor --server.port=8180'
```


Simulating a registration of a farmer with his personal data

```httpie
http -v  :8080/api/v1/geoLocation name=Ady geoLocation="55.76, 45.23"
```