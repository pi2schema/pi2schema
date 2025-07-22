# JSON Schema Provider Example

This example demonstrates how to use the JSON Schema provider with Spring Boot and Kafka.

## Usage in Spring Boot Application

### 1. Application Properties

```properties
# JSON Schema specific configuration
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.properties.interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareJsonSchemaProducerInterceptor

spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.properties.interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareJsonSchemaConsumerInterceptor

# PII Configuration
spring.kafka.properties.pi2schema.personal.metadata.provider=pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider
spring.kafka.properties.pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider

# Schema Registry (if using)
spring.kafka.properties.schema.registry.url=http://localhost:8081/
```

### 2. JSON Schema Definition

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "User Registration Event",
  "type": "object",
  "properties": {
    "userId": {
      "type": "string",
      "description": "Unique user identifier",
      "pi2schema-subject-identifier": true
    },
    "email": {
      "description": "User email address (PII)",
      "oneOf": [
        {
          "type": "string",
          "format": "email",
          "pi2schema-personal-data": true
        },
        {
          "$ref": "#/$defs/EncryptedPersonalData"
        }
      ]
    },
    "profile": {
      "type": "object",
      "properties": {
        "firstName": {
          "oneOf": [
            {
              "type": "string",
              "pi2schema-personal-data": true
            },
            {
              "$ref": "#/$defs/EncryptedPersonalData"
            }
          ]
        },
        "lastName": {
          "oneOf": [
            {
              "type": "string", 
              "pi2schema-personal-data": true
            },
            {
              "$ref": "#/$defs/EncryptedPersonalData"
            }
          ]
        },
        "age": {
          "type": "integer",
          "minimum": 0
        }
      }
    },
    "preferences": {
      "type": "object",
      "properties": {
        "newsletter": {"type": "boolean"},
        "theme": {"type": "string"}
      }
    }
  },
  "required": ["userId", "email"],
  "$defs": {
    "EncryptedPersonalData": {
      "type": "object",
      "properties": {
        "subjectId": {"type": "string"},
        "data": {"type": "string", "format": "base64"},
        "personalDataFieldNumber": {"type": "string"},
        "usedTransformation": {"type": "string"},
        "initializationVector": {"type": "string", "format": "base64"},
        "kmsId": {"type": "string"}
      },
      "required": ["subjectId", "data", "usedTransformation", "initializationVector"]
    }
  }
}
```

### 3. Service Implementation

```java
@Service
public class UserRegistrationService {
    
    private final JsonSchemaPersonalMetadataProvider metadataProvider;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public UserRegistrationService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.metadataProvider = new JsonSchemaPersonalMetadataProvider();
        this.objectMapper = new ObjectMapper();
    }
    
    public void registerUser(Map<String, Object> userData) {
        try {
            // The interceptor will automatically encrypt PII fields
            String jsonData = objectMapper.writeValueAsString(userData);
            kafkaTemplate.send("user-registrations", jsonData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send user registration", e);
        }
    }
}
```

### 4. Consumer Implementation

```java
@Component
public class UserRegistrationConsumer {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @KafkaListener(topics = "user-registrations")
    public void handleUserRegistration(String jsonData) {
        try {
            // The interceptor will automatically decrypt PII fields
            Map<String, Object> userData = objectMapper.readValue(jsonData, Map.class);
            
            // Process decrypted user data
            String userId = (String) userData.get("userId");
            String email = (String) userData.get("email"); // Decrypted automatically
            
            Map<String, Object> profile = (Map<String, Object>) userData.get("profile");
            String firstName = (String) profile.get("firstName"); // Decrypted automatically
            
            // Business logic here...
            processUserRegistration(userId, email, firstName);
            
        } catch (Exception e) {
            log.error("Failed to process user registration", e);
        }
    }
    
    private void processUserRegistration(String userId, String email, String firstName) {
        // Your business logic here
        log.info("Processing registration for user: {} with email: {}", firstName, email);
    }
}
```

## Manual Usage (Without Kafka Interceptors)

```java
@Service
public class ManualPiiHandlingService {
    
    private final JsonSchemaPersonalMetadataProvider metadataProvider;
    private final Encryptor encryptor;
    private final Decryptor decryptor;
    
    public void processUserData(Map<String, Object> userData, String schemaContent) {
        // Analyze schema and create metadata
        var metadata = metadataProvider.forSchema(schemaContent);
        
        if (metadata.requiresEncryption()) {
            // Encrypt PII fields
            var encryptedData = metadata.swapToEncrypted(encryptor, userData);
            
            // Send encrypted data...
            sendToKafka(encryptedData);
            
            // Later, decrypt the data
            var decryptedData = metadata.swapToDecrypted(decryptor, encryptedData);
            
            // Use decrypted data...
            processDecryptedData(decryptedData);
        }
    }
}
```

## Benefits

- **Automatic PII Protection**: PII fields are automatically encrypted/decrypted
- **Schema-Driven**: PII handling is defined in the JSON Schema itself
- **Transparent**: Application code doesn't need to change for encryption
- **Compatible**: Works with existing Kafka infrastructure
- **Flexible**: Supports nested objects and complex data structures
