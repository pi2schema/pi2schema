---
title: Schema Provider Discoverability and Configuration for Pi2Schema Framework
version: 1.0
date_created: 2025-08-14
last_updated: 2025-08-14
owner: pi2schema
tags: [schema, spi, discovery, configuration, adapter, provider]
---

# Schema Provider Discoverability and Configuration for Pi2Schema Framework

A specification defining the discoverability and configuration mechanisms for `SchemaProvider` implementations within the pi2schema framework, enabling adapters to automatically or explicitly locate appropriate schema providers for different schema formats while maintaining architectural separation of concerns.

## 1. Purpose & Scope

This specification defines how adapters (particularly Kafka interceptors) can discover and instantiate appropriate `SchemaProvider` implementations to work alongside `PersonalMetadataProvider` instances. The goal is to migrate from the deprecated `forType()` method to the new `forSchema()` method while providing flexible configuration options for provider discovery.

**Scope includes:**
- Configuration-based provider discovery mechanisms
- Service Provider Interface (SPI) automatic discovery
- Hybrid discovery strategies combining configuration and SPI
- Integration patterns for Kafka adapters
- Performance and caching considerations

**Out of scope:**
- Schema definition formats and parsing
- Personal metadata analysis logic
- Cryptographic operations

## 2. Definitions

- **Schema Provider Discovery**: The process of locating and instantiating appropriate `SchemaProvider` implementations
- **SPI (Service Provider Interface)**: Java's standard mechanism for discovering implementations using `ServiceLoader`
- **Discovery Strategy**: The approach used to locate providers (configuration, SPI, or hybrid)
- **Provider Registry**: A runtime cache of discovered and instantiated providers
- **Type Matching**: The process of determining which provider supports a given schema type
- **Fallback Provider**: A default provider used when no specific provider is found
- **Configuration Priority**: The order in which discovery mechanisms are applied

## 3. Requirements, Constraints & Guidelines

### Core Discovery Requirements

- **REQ-001**: The system SHALL support explicit configuration-based provider discovery
- **REQ-002**: The system SHALL support automatic SPI-based provider discovery as a fallback
- **REQ-003**: Provider discovery SHALL be performed once per adapter instance and cached
- **REQ-004**: The system SHALL support multiple schema providers in a single application
- **REQ-005**: Discovery mechanisms SHALL handle provider instantiation failures gracefully
- **REQ-006**: The system SHALL log provider discovery decisions for debugging

### Configuration Requirements

- **REQ-007**: Schema provider configuration SHALL follow Kafka configuration patterns
- **REQ-008**: Configuration properties SHALL be prefixed with `pi2schema.schema.provider`
- **REQ-009**: Configuration validation SHALL occur during adapter initialization
- **REQ-010**: Invalid configurations SHALL fail fast with clear error messages

### SPI Requirements

- **REQ-011**: SPI discovery SHALL use standard Java `ServiceLoader` mechanisms with `module-info.java` declarations
- **REQ-012**: Provider implementations SHALL declare services using `module-info.java` with `provides` clauses
- **REQ-013**: External dependencies SHALL be handled as unnamed modules to avoid forced modularization
- **REQ-014**: SPI discovery SHALL be compatible with different classloader environments
- **REQ-015**: Internal pi2schema modules SHALL be fully modularized while external dependencies remain on classpath

### Performance Constraints

- **CON-001**: Provider discovery SHALL NOT impact adapter runtime performance (discovery occurs only once during configuration)
- **CON-002**: SPI scanning SHALL occur only during initialization
- **CON-003**: Provider instantiation SHALL be lazy where possible

### Integration Constraints

- **CON-004**: Discovery mechanisms SHALL be compatible with Kafka interceptor lifecycle
- **CON-005**: The implementation SHALL NOT require changes to existing `PersonalMetadataProvider` implementations
- **CON-006**: Configuration SHALL be backward compatible with existing adapter configurations
- **CON-007**: The system SHALL work in both standalone and distributed Kafka environments

### Implementation Guidelines

- **GUD-001**: Use simple constructor patterns for provider instantiation
- **GUD-002**: Implement defensive programming for provider discovery failures
- **GUD-003**: Provide clear documentation for SPI service registration
- **GUD-004**: Follow existing pi2schema package structure patterns
- **GUD-005**: Use dependency injection patterns where applicable

## 4. Interfaces & Data Contracts

### Core Discovery Interface

```java
/**
 * Service interface for discovering SchemaProvider implementations.
 * Supports configuration-based discovery with SPI fallback.
 */
public interface SchemaProviderDiscovery {
    
    /**
     * Discovers and returns an appropriate SchemaProvider for the given configuration.
     * @param configs Adapter configuration properties
     * @return SchemaProvider instance
     * @throws SchemaProviderDiscoveryException if no suitable provider is found
     */
    <S> SchemaProvider<S> discoverProvider(Map<String, ?> configs) 
        throws SchemaProviderDiscoveryException;
    
    /**
     * Lists all available SchemaProvider implementations.
     * @return Collection of discovered provider descriptors
     */
    Collection<SchemaProviderDescriptor> listAvailableProviders();
}
```

### Discovery Strategy Interface

```java
/**
 * Interface for implementing different provider discovery strategies.
 * Each strategy focuses on a single discovery mechanism.
 */
public interface SchemaProviderDiscoveryStrategy {
    
    /**
     * Attempts to discover a SchemaProvider using this strategy.
     * @param configs Configuration properties
     * @return Optional SchemaProvider if found, empty if this strategy cannot locate a provider
     */
    <S> Optional<SchemaProvider<S>> discover(Map<String, ?> configs);
    
    /**
     * Strategy name for identification and configuration.
     * @return Strategy name (e.g., "configuration", "module-system-spi")
     */
    String getStrategyName();
    
    /**
     * Priority for strategy ordering (higher values = higher priority).
     * @return Strategy priority (e.g., 100 for configuration, 50 for SPI)
     */
    int getPriority();
    
    /**
     * Indicates whether this strategy is available in the current environment.
     * @return true if strategy can be used, false otherwise
     */
    boolean isAvailable();
}
```

### Provider Descriptor

```java
/**
 * Descriptor for discovered SchemaProvider implementations.
 */
public class SchemaProviderDescriptor {
    private final String providerId;
    private final Class<? extends SchemaProvider<?>> providerClass;
    private final Class<?> schemaType;
    private final Set<String> supportedFormats;
    private final String strategyName;
    
    public SchemaProviderDescriptor(String providerId, 
                                  Class<? extends SchemaProvider<?>> providerClass,
                                  Class<?> schemaType,
                                  Set<String> supportedFormats,
                                  String strategyName) {
        this.providerId = providerId;
        this.providerClass = providerClass;
        this.schemaType = schemaType;
        this.supportedFormats = Set.copyOf(supportedFormats);
        this.strategyName = strategyName;
    }
    
    // Getters and utility methods
    public String getProviderId() { return providerId; }
    public Class<? extends SchemaProvider<?>> getProviderClass() { return providerClass; }
    public Class<?> getSchemaType() { return schemaType; }
    public Set<String> getSupportedFormats() { return supportedFormats; }
    public String getStrategyName() { return strategyName; }
}
```

### Configuration Properties

```java
public class SchemaProviderConfig {
    
    // Primary configuration property
    public static final String SCHEMA_PROVIDER_CLASS = "pi2schema.schema.provider.class";
}
```

### Updated Adapter Integration Pattern

```java
public class KafkaGdprAwareProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private SchemaProvider<Object> schemaProvider;
    private PersonalMetadataProvider<V, Object> metadataProvider;
    private SchemaProviderDiscovery discovery;
    
    @Override
    public void configure(Map<String, ?> configs) {
        var piiAwareInterceptorConfig = new PiiAwareInterceptorConfig(configs);
        
        // Initialize discovery service with default strategies
        this.discovery = new DefaultSchemaProviderDiscovery();
        
        // Discover schema provider
        try {
            this.schemaProvider = discovery.discoverProvider(configs);
        } catch (SchemaProviderDiscoveryException e) {
            throw new IllegalStateException("Failed to discover SchemaProvider", e);
        }
        
        // Configure metadata provider (existing pattern)
        this.metadataProvider = piiAwareInterceptorConfig.getConfiguredInstance(
            PERSONAL_METADATA_PROVIDER_CONFIG, PersonalMetadataProvider.class);
    }
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record == null || record.value() == null) return record;
        
        // Step 1: Discover schema definition
        Object schema = schemaProvider.schemaFor(record.value());
        
        // Step 2: Analyze schema for PII metadata
        PersonalMetadata<V> metadata = metadataProvider.forSchema(schema);
        
        // Step 3: Perform encryption
        V encryptedValue = metadata.swapToEncrypted(localEncryptor, record.value());
        
        return new ProducerRecord<>(
            record.topic(),
            record.partition(), 
            record.timestamp(),
            record.key(),
            encryptedValue
        );
    }
}
```

### Acceptance Criteria

- **AC-001**: Given explicit schema provider configuration, When adapter initializes, Then specified provider is used without SPI scanning
- **AC-002**: Given no explicit configuration, When single SPI provider is available, Then that provider is discovered automatically  
- **AC-003**: Given no explicit configuration, When multiple SPI providers are available, Then clear error message instructs to use explicit configuration
- **AC-004**: Given invalid provider configuration, When adapter initializes, Then SPI discovery is used as fallback
- **AC-005**: Given successful provider discovery, When multiple records are processed, Then same provider instance is reused (adapter-level caching)

## 6. Test Automation Strategy

### Test Levels
- **Unit Tests**: Individual discovery strategy implementations
- **Integration Tests**: Full discovery service with multiple strategies
- **End-to-End Tests**: Complete adapter workflow with discovered providers

### Test Framework Requirements
- **Framework**: JUnit 5 with Mockito for mocking
- **Configuration Testing**: Test various configuration combinations
- **SPI Testing**: Mock ServiceLoader behavior and classpath scenarios
- **Performance Testing**: Measure discovery overhead and caching effectiveness

### Test Scenarios
- **Configuration-based discovery success and failure cases**
- **SPI discovery with single provider (success)**
- **SPI discovery with multiple providers (conflict detection)**
- **Fallback behavior from configuration to SPI**
- **Cache behavior and performance**
- **Error handling and logging**

## 7. Rationale & Context

### Design Decisions

**Simple Default Strategy**: The discovery system uses a fixed strategy order: configuration first, then module-system-spi fallback. This eliminates configuration complexity while providing the most common use cases.

**Strong Defaults**: Developers can start with zero configuration for schema providers - the system will automatically discover providers via SPI. Explicit configuration is only needed for override scenarios.

**Minimal Configuration Surface**: Only one configuration property is needed: `pi2schema.schema.provider.class` for explicit providers when multiple providers are available.

### Strategy Responsibilities

**`DefaultSchemaProviderDiscovery`**:
- Executes fixed strategy order: configuration, then module-system-spi
- Handles error aggregation and reporting
- Provides debug logging for discovery decisions
- Uses default constructor with sensible strategy defaults

**Individual Discovery Strategies**:
- Focus on single discovery mechanism (configuration or SPI)
- Return `Optional<SchemaProvider<S>>` to indicate success/failure
- Handle strategy-specific error cases gracefully
- Provide clear identification via `getStrategyName()`

### Integration with Existing Architecture

The discovery mechanism integrates with the existing adapter configuration pattern while enabling the migration from `forType()` to `forSchema()` methods. The simple strategy approach provides:

- **Zero Configuration**: Developers can use SPI discovery without any configuration
- **Simple Override**: Explicit configuration takes precedence when needed  
- **Backward Compatibility**: Existing explicit configuration continues to work
- **Future Extensibility**: Strategy pattern allows adding new mechanisms later without changing the API

### Java Module System with Unnamed Module Strategy

The SchemaProvider discovery system will use JPMS for internal pi2schema modules while treating external dependencies as unnamed modules:

#### Direct Module System Approach

All pi2schema submodules will have `module-info.java` declarations, allowing external dependencies (Kafka, Jackson, etc.) to remain on the classpath as unnamed modules.

#### Internal Module Structure
```java
// schema-spi/src/main/java/module-info.java
module pi2schema.schema.spi {
    exports pi2schema.schema;                    // Contains SchemaProvider interface
    exports pi2schema.schema.personaldata;      // Contains PersonalMetadataProvider interface
    
    uses pi2schema.schema.SchemaProvider;
    uses pi2schema.schema.personaldata.PersonalMetadataProvider;
}

// schema-providers-jsonschema/src/main/java/module-info.java
module pi2schema.schema.providers.jsonschema {
    requires pi2schema.schema.spi;
    // External deps as unnamed modules - no explicit requires
    
    provides pi2schema.schema.SchemaProvider 
        with pi2schema.schema.providers.jsonschema.JsonSchemaProvider;
    
    provides pi2schema.schema.personaldata.PersonalMetadataProvider 
        with pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;
    
    exports pi2schema.schema.providers.jsonschema;
}

// serialization-adapters-kafka/src/main/java/module-info.java
module pi2schema.serialization.adapters.kafka {
    requires pi2schema.schema.spi;
    // Kafka client libs remain as unnamed modules
    
    uses pi2schema.schema.SchemaProvider;
    uses pi2schema.schema.personaldata.PersonalMetadataProvider;
}
```

#### Discovery Implementation

The module system SPI discovery is handled by the `ModuleSystemSpiDiscoveryStrategy` class, which integrates seamlessly with the `DefaultSchemaProviderDiscovery` orchestrator. This approach maintains clean separation between discovery orchestration and individual discovery mechanisms.

#### Benefits of Unnamed Module Strategy

- **Clean Internal Structure**: Full JPMS benefits for pi2schema modules
- **No External Dependency Migration**: Kafka, Jackson, etc. work as-is
- **Compile-time Safety**: Service declarations validated at compile time
- **Simplified Build**: No complex module path configuration for external deps
- **Performance**: Better service loading performance with module system

#### Gradle Build Configuration
```kotlin
// Each submodule build.gradle.kts
java {
    modularity.inferModulePath.set(true)
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "--module-path", configurations.compileClasspath.get().asPath
    ))
}
```

This approach provides the maintainability and safety of JPMS for internal architecture while avoiding the complexity of modularizing the entire dependency chain.

#### Implementation Considerations

**Module Path vs Classpath**: Pi2schema modules go on module path, external dependencies (Kafka, Jackson) remain on classpath as unnamed modules.

**ServiceLoader Behavior**: When modules are on module path and use `provides` clauses, ServiceLoader automatically discovers services without META-INF/services files.

**Build Tool Integration**: Gradle/Maven can be configured to automatically detect modules and handle the module/classpath split.

**Migration Path**: Existing pi2schema installations work unchanged - the module system is additive and doesn't break classpath-based usage.

## 8. Dependencies & External Integrations

### Framework Dependencies
- **DEP-001**: Java ServiceLoader for SPI discovery
- **DEP-002**: Kafka client configuration framework for property handling  
- **DEP-003**: SLF4J for consistent logging patterns

### Integration Dependencies
- **INT-001**: Existing SchemaProvider SPI interfaces
- **INT-002**: PersonalMetadataProvider implementations
- **INT-003**: Kafka interceptor lifecycle management
- **INT-004**: Pi2schema configuration framework

### Runtime Dependencies
- **RUN-001**: Proper service registration via `module-info.java` provides clauses
- **RUN-002**: Module path configuration for pi2schema modules with classpath for external dependencies
- **RUN-003**: Valid Kafka configuration properties
- **RUN-004**: Java 9+ module system support
- **RUN-005**: Correct module layer configuration with unnamed module support

## 9. Examples & Edge Cases

### Configuration Examples

#### Explicit Provider Configuration (Simplest Approach)
```properties
# Kafka interceptor configuration - explicit provider
pi2schema.schema.provider.class=pi2schema.schema.providers.jsonschema.JsonSchemaProvider
pi2schema.personal.metadata.provider=pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider
```

#### Automatic Discovery (Zero Configuration)
```properties
# No schema provider configuration needed - automatic SPI discovery
pi2schema.personal.metadata.provider=pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider
```

### Practical Pi2Schema Module Structure

Based on the current pi2schema project structure, here's how the modules would be organized:

```
// schema-spi/src/main/java/module-info.java
module pi2schema.schema.spi {
    exports pi2schema.schema;                    // Contains SchemaProvider interface
    exports pi2schema.schema.personaldata;      // Contains PersonalMetadataProvider interface
    
    uses pi2schema.schema.SchemaProvider;
    uses pi2schema.schema.personaldata.PersonalMetadataProvider;
}

// schema-providers-jsonschema/src/main/java/module-info.java
module pi2schema.schema.providers.jsonschema {
    requires pi2schema.schema.spi;
    
    provides pi2schema.schema.SchemaProvider 
        with pi2schema.schema.providers.jsonschema.JsonSchemaProvider;
    
    provides pi2schema.schema.personaldata.PersonalMetadataProvider 
        with pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;
    
    exports pi2schema.schema.providers.jsonschema;
}

// schema-providers-avro/src/main/java/module-info.java
module pi2schema.schema.providers.avro {
    requires pi2schema.schema.spi;
    
    provides pi2schema.schema.SchemaProvider 
        with pi2schema.schema.providers.avro.AvroSchemaProvider;
    
    provides pi2schema.schema.personaldata.PersonalMetadataProvider 
        with pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider;
}

// schema-providers-protobuf/src/main/java/module-info.java
module pi2schema.schema.providers.protobuf {
    requires pi2schema.schema.spi;
    
    provides pi2schema.schema.SchemaProvider 
        with pi2schema.schema.providers.protobuf.ProtobufSchemaProvider;
    
    provides pi2schema.schema.personaldata.PersonalMetadataProvider 
        with pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider;
}

// serialization-adapters-kafka/src/main/java/module-info.java
module pi2schema.serialization.adapters.kafka {
    requires pi2schema.schema.spi;
    // Kafka client jars remain as unnamed modules on classpath
    
    uses pi2schema.schema.SchemaProvider;
    uses pi2schema.schema.personaldata.PersonalMetadataProvider;
    
    exports pi2schema.serialization.adapters.kafka;
}
```

### Build Configuration Example

```kotlin
// Each provider module's build.gradle.kts
plugins {
    `java-library`
}

java {
    modularity.inferModulePath.set(true)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation(project(":schema-spi"))
    
    // External deps go to classpath (unnamed modules)
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.networknt:json-schema-validator:1.0.87")
    
    // No need to modularize these - they work as unnamed modules
    implementation("org.apache.kafka:kafka-clients:3.5.1")
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "--add-reads", "pi2schema.schema.providers.jsonschema=ALL-UNNAMED"
    ))
}
```

### SPI Service Registration

#### Example service registration via module-info.java
```java
// schema-providers-jsonschema/src/main/java/module-info.java  
module pi2schema.schema.providers.jsonschema {
    requires pi2schema.schema.spi;
    
    provides pi2schema.schema.SchemaProvider 
        with pi2schema.schema.providers.jsonschema.JsonSchemaProvider;
    
    provides pi2schema.schema.personaldata.PersonalMetadataProvider 
        with pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider;
}
```

### Discovery Strategy Implementations

#### Configuration-Based Discovery Strategy
```java
/**
 * Strategy that discovers providers through explicit configuration.
 * Highest priority strategy that uses configured class names.
 */
public class ConfigurationDiscoveryStrategy implements SchemaProviderDiscoveryStrategy {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationDiscoveryStrategy.class);
    
    @Override
    public <S> Optional<SchemaProvider<S>> discover(Map<String, ?> configs) {
        String providerClass = (String) configs.get(SchemaProviderConfig.SCHEMA_PROVIDER_CLASS);
        
        if (providerClass == null || providerClass.trim().isEmpty()) {
            LOG.debug("No explicit schema provider configured");
            return Optional.empty();
        }
        
        try {
            LOG.debug("Attempting to instantiate configured provider: {}", providerClass);
            
            Class<?> clazz = Class.forName(providerClass);
            if (!SchemaProvider.class.isAssignableFrom(clazz)) {
                LOG.error("Configured class {} does not implement SchemaProvider", providerClass);
                return Optional.empty();
            }
            
            @SuppressWarnings("unchecked")
            SchemaProvider<S> provider = (SchemaProvider<S>) clazz.getDeclaredConstructor().newInstance();
            
            LOG.info("Successfully instantiated configured SchemaProvider: {}", providerClass);
            return Optional.of(provider);
            
        } catch (ClassNotFoundException e) {
            LOG.error("Configured SchemaProvider class not found: {}", providerClass, e);
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Failed to instantiate configured SchemaProvider: {}", providerClass, e);
            return Optional.empty();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "configuration";
    }
    
    @Override
    public int getPriority() {
        return 100; // Highest priority
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Always available
    }
}
```

#### Module System SPI Discovery Strategy
```java
/**
 * Strategy that discovers providers through Java Module System SPI.
 * Uses module-info.java service declarations for discovery.
 */
public class ModuleSystemSpiDiscoveryStrategy implements SchemaProviderDiscoveryStrategy {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleSystemSpiDiscoveryStrategy.class);
    
    @Override
    public <S> Optional<SchemaProvider<S>> discover(Map<String, ?> configs) {
        if (!isAvailable()) {
            LOG.debug("Module system not available, skipping module SPI discovery");
            return Optional.empty();
        }
        
        LOG.debug("Searching for SchemaProvider via module system SPI");
        
        List<SchemaProvider<?>> availableProviders = ServiceLoader.load(SchemaProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
        
        if (availableProviders.isEmpty()) {
            LOG.debug("No SchemaProvider found via module system SPI");
            return Optional.empty();
        }
        
        if (availableProviders.size() > 1) {
            String providerNames = availableProviders.stream()
                    .map(p -> p.getClass().getName())
                    .collect(Collectors.joining(", "));
            
            throw new SchemaProviderDiscoveryException(
                    String.format("Multiple SchemaProvider implementations found: [%s]. " +
                                "Please specify which provider to use with '%s' configuration property.",
                                providerNames, SchemaProviderConfig.SCHEMA_PROVIDER_CLASS));
        }
        
        SchemaProvider<?> provider = availableProviders.get(0);
        LOG.info("Discovered SchemaProvider via module system SPI: {}", provider.getClass().getName());
        return Optional.of((SchemaProvider<S>) provider);
    }
    
    private boolean isCompatible(SchemaProvider<?> provider, String schemaTypeHint, Map<String, ?> configs) {
        // Future enhancement: This method could be used when schema type hints
        // are automatically derived from the metadata provider
        return true;
    }
    
    @Override
    public String getStrategyName() {
        return "module-system-spi";
    }
    
    @Override
    public int getPriority() {
        return 80; // High priority, but lower than explicit configuration
    }
    
    @Override
    public boolean isAvailable() {
        // Check if we're running with module system
        return ModuleLayer.boot() != null && 
               !ModuleLayer.boot().configuration().modules().isEmpty();
    }
}
```

#### Orchestrating Discovery Service
```java
/**
 * Default implementation that orchestrates multiple discovery strategies.
 * Executes strategies in priority order.
 */
public class DefaultSchemaProviderDiscovery implements SchemaProviderDiscovery {
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSchemaProviderDiscovery.class);
    
    private final List<SchemaProviderDiscoveryStrategy> strategies;
    
    /**
     * Default constructor with fixed strategies: configuration first, then module-system-spi.
     */
    public DefaultSchemaProviderDiscovery() {
        this(Arrays.asList(
            new ConfigurationDiscoveryStrategy(),
            new ModuleSystemSpiDiscoveryStrategy()
        ));
    }
    
    /**
     * Constructor for custom strategies (primarily for testing).
     * @param strategies Custom list of discovery strategies
     */
    public DefaultSchemaProviderDiscovery(List<SchemaProviderDiscoveryStrategy> strategies) {
        // Sort strategies by priority (highest first) and filter available ones
        this.strategies = strategies.stream()
                .filter(SchemaProviderDiscoveryStrategy::isAvailable)
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toUnmodifiableList());
        
        LOG.info("Initialized SchemaProviderDiscovery with strategies: {}", 
                this.strategies.stream()
                        .map(SchemaProviderDiscoveryStrategy::getStrategyName)
                        .collect(Collectors.joining(", ")));
    }
    
    @Override
    public <S> SchemaProvider<S> discoverProvider(Map<String, ?> configs) 
            throws SchemaProviderDiscoveryException {
        
        List<String> attemptedStrategies = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        
        // Try each strategy in priority order
        for (SchemaProviderDiscoveryStrategy strategy : strategies) {
            attemptedStrategies.add(strategy.getStrategyName());
            
            try {
                LOG.debug("Attempting discovery with strategy: {}", strategy.getStrategyName());
                
                Optional<SchemaProvider<S>> provider = strategy.discover(configs);
                if (provider.isPresent()) {
                    SchemaProvider<S> discoveredProvider = provider.get();
                    
                    LOG.info("Successfully discovered SchemaProvider using strategy: {} -> {}", 
                            strategy.getStrategyName(), 
                            discoveredProvider.getClass().getName());
                    
                    return discoveredProvider;
                }
                
                LOG.debug("Strategy {} did not find a provider", strategy.getStrategyName());
                
            } catch (Exception e) {
                String errorMsg = String.format("Strategy %s failed: %s", 
                        strategy.getStrategyName(), e.getMessage());
                failureReasons.add(errorMsg);
                LOG.warn("Discovery strategy {} encountered error", strategy.getStrategyName(), e);
            }
        }
        
        // All strategies failed
        String errorMessage = String.format(
                "No SchemaProvider found after trying strategies: %s. Failures: %s",
                String.join(", ", attemptedStrategies),
                String.join("; ", failureReasons));
        
        LOG.error(errorMessage);
        throw new SchemaProviderDiscoveryException(errorMessage);
    }
    
    @Override
    public Collection<SchemaProviderDescriptor> listAvailableProviders() {
        List<SchemaProviderDescriptor> descriptors = new ArrayList<>();
        
        // Collect providers from all available strategies
        for (SchemaProviderDiscoveryStrategy strategy : strategies) {
            try {
                // Attempt discovery with empty config to see what's available
                Optional<SchemaProvider<?>> provider = strategy.discover(Collections.emptyMap());
                if (provider.isPresent()) {
                    SchemaProvider<?> p = provider.get();
                    descriptors.add(new SchemaProviderDescriptor(
                            p.getClass().getSimpleName(),
                            (Class<? extends SchemaProvider<?>>) p.getClass(),
                            Object.class, // Generic schema type
                            p.getSupportedSchemaTypes(),
                            strategy.getStrategyName()
                    ));
                }
            } catch (Exception e) {
                LOG.debug("Strategy {} failed during provider listing", strategy.getStrategyName(), e);
            }
        }
        
        return descriptors;
    }
}
```

### Edge Cases

- **Multiple Schema Types**: Application uses both JSON and Avro schemas requiring different providers - resolved by explicit configuration when multiple providers are available
- **Provider Conflicts**: Multiple SPI providers available - explicit error message instructs user to configure explicit provider
- **Configuration Errors**: Invalid provider class names or missing required properties - fallback to SPI discovery
- **Performance Constraints**: Discovery overhead in high-throughput scenarios - mitigated by single discovery during configuration
- **Classloader Isolation**: Different providers loaded in separate classloaders - each strategy handles its own classloader context


## 10. Validation Criteria

- **Simple Strategy Execution**: Fixed strategy order (configuration → module-system-spi) with explicit conflict detection
- **Configuration Validation**: Invalid configurations are caught and reported clearly, with SPI fallback
- **SPI Discovery**: Module system SPI discovery works correctly, with clear error messages for conflicts
- **Error Handling**: Failed strategies provide clear debugging information and automatic fallback
- **Conflict Detection**: Multiple SPI providers result in explicit error message directing user to configuration
- **Performance**: Strategy execution shows negligible impact on message processing throughput (discovery occurs only once during configuration)
- **Integration**: Complete adapter workflow operates successfully with discovered providers
- **Zero Configuration**: Adapters work without any schema provider configuration when single SPI provider is available

## 11. Related Specifications / Further Reading

- [Schema Definition Provider Architecture](spec-schema-definition-provider.md)
- [Schema Definition Provider - JSON Schema Implementation](spec-schema-definition-provider-jsonschema.md)
- [Schema Definition Provider - Protobuf Implementation](spec-schema-definition-provider-protobuf.md)
- [Schema Definition Provider - Avro Implementation](spec-schema-definition-provider-avro.md)
- [Kafka Client Configuration Documentation](https://kafka.apache.org/documentation/#producerconfigs)
- [Java ServiceLoader Documentation](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
- [Apache Flink Plugin Discovery Patterns](https://github.com/apache/flink/tree/main/flink-core/src/main/java/org/apache/flink/core/plugin)
