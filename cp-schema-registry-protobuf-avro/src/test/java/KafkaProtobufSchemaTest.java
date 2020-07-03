import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import origins.proto.UrnOuterClass.Urn;
import origins.proto.any.EnvelopAnyOuterClass;
import origins.proto.any.EnvelopAnyOuterClass.EnvelopAny;
import origins.proto.oneof.EnvelopOneOfOuterClass;
import origins.proto.oneof.EnvelopOneOfOuterClass.EnvelopOneOf;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaProtobufSchemaTest {


    static final Map<String, String> KAFKA_PRODUCER_CONFIG = Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName()
    );

    static final Map<String, String> KAFKA_CONSUMER_CONFIG = Map.of(
            ConsumerConfig.GROUP_ID_CONFIG, "test-proto",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class.getName(),
            KafkaProtobufDeserializerConfig.DERIVE_TYPE_CONFIG, "true"
    );

    private static final String TOPIC_PROTOBUF_ONE_OF = "origins-one-of";
    private static final String TOPIC_PROTOBUF_ANY = "origins-any";

    private static Properties consumerConfig;
    private static Properties producerConfig;

    @BeforeAll
    public static void kafkaConfigurationProperties() {
        producerConfig = new Properties();
        producerConfig.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        producerConfig.putAll(KAFKA_PRODUCER_CONFIG);

        consumerConfig = new Properties();
        consumerConfig.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        consumerConfig.putAll(KAFKA_CONSUMER_CONFIG);
    }

    @BeforeAll
    public static void createTopicsIfNotExistent(){
        Properties adminProperties = new Properties();
        adminProperties.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        AdminClient adminClient = AdminClient.create(adminProperties);

        NewTopic oneOf = new NewTopic(TOPIC_PROTOBUF_ONE_OF, 1, (short) 1);
        NewTopic any = new NewTopic(TOPIC_PROTOBUF_ANY, 1, (short) 1);
    adminClient.createTopics(List.of(oneOf, any));
    }


    @Test
    void testAny() throws InterruptedException {

        List<EnvelopAny> events = List.of(
                EnvelopAny.newBuilder()
                        .setCausation(randomUrn())
                        .setEvent(Any.pack(EnvelopAnyOuterClass.Harvested.newBuilder()
                                .setId(randomUrn())
                                .setHarvestedAt(now())
                                .setHarvestedBy(randomUrn())
                                .build()))
                        .build(),
                EnvelopAny.newBuilder()
                        .setEvent(Any.pack(EnvelopAnyOuterClass.RetailerReceived.newBuilder()
                                .setId(randomUrn())
                                .setReceivedAt(now())
                                .setReceivedBy(randomUrn())
                                .build()))
                        .build());

        KafkaProducer<String, EnvelopAny> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(TOPIC_PROTOBUF_ANY, e.getCausation().getIdentifier(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        KafkaConsumerRunner<String, EnvelopAny> consumer = new KafkaConsumerRunner<>(
                consumerConfig,
                TOPIC_PROTOBUF_ANY,
                record -> {

                    try {
                        Any event = record.value().getEvent();

                        if (event.is(EnvelopAnyOuterClass.Harvested.class)) {
                            log("Handling typed harvested ev %s", event.unpack(EnvelopAnyOuterClass.Harvested.class));
                        } else if (event.is(EnvelopAnyOuterClass.RetailerReceived.class)) {
                            log("Handling typed retailer received ev %s", event.unpack(EnvelopAnyOuterClass.RetailerReceived.class));
                        } else {
                            log("Ignoring unknown event %s", event);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }

                    consumptionLatch.countDown();
                });

        new Thread(consumer).start();

        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());

    }

    @Test
    public void testOneOf() throws InterruptedException {

        List<EnvelopOneOf> events = List.of(EnvelopOneOf.newBuilder()
                        .setHarvested(EnvelopOneOfOuterClass.Harvested.newBuilder()
                                .setId(randomUrn())
                                .setHarvestedAt(now())
                                .setHarvestedBy(randomUrn()))
                        .build(),
                EnvelopOneOf.newBuilder()
                        .setRetailerReceived(EnvelopOneOfOuterClass.RetailerReceived.newBuilder()
                                .setId(randomUrn())
                                .setReceivedAt(now())
                                .setReceivedBy(randomUrn()))
                        .build());

        KafkaProducer<String, EnvelopOneOf> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(TOPIC_PROTOBUF_ONE_OF, e.getCausation().getIdentifier(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        KafkaConsumerRunner<String, EnvelopOneOf> consumer = new KafkaConsumerRunner<>(
                consumerConfig,
                TOPIC_PROTOBUF_ONE_OF,
                record -> {
                    EnvelopOneOf message = record.value();
                    log("Received event case %s", message.getEventCase());

                    switch (message.getEventCase()) {
                        case HARVESTED -> log("Handling typed harvested ev %s", message.getHarvested());
                        case RETAILERRECEIVED -> log("Handling typed retailer received ev %s", message.getRetailerReceived());
                        default -> log("Ignoring unknown event %s", message);
                    }

                    consumptionLatch.countDown();
                });
        new Thread(consumer).start();

        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());

//        adminClient.deleteTopics(asList(newTopic.name()));
    }


    @Test
    void consumeAllFromOneOf() throws ExecutionException, InterruptedException {

        Properties adminProperties = new Properties();
        adminProperties.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        AdminClient adminClient = AdminClient.create(adminProperties);

        Map<TopicPartition, OffsetAndMetadata> current = adminClient.listConsumerGroupOffsets("test-proto")
                .partitionsToOffsetAndMetadata()
                .get();

        log("current topic description: %s", current);

        TopicPartition topicPartition = new TopicPartition(TOPIC_PROTOBUF_ONE_OF, 0);
        adminClient.alterConsumerGroupOffsets("test-proto", Map.of(topicPartition, new OffsetAndMetadata(0L)));

        CountDownLatch consumptionLatch = new CountDownLatch((int) current.get(topicPartition).offset());
        KafkaConsumerRunner<String, EnvelopOneOf> consumer = new KafkaConsumerRunner<>(
                consumerConfig,
                TOPIC_PROTOBUF_ONE_OF,
                record -> {
                    consumptionLatch.countDown();

                    log("Received event case %s", record.value().getEventCase());

                    switch (record.value().getEventCase()) {
                        case HARVESTED -> log("Handling typed harvested ev %s", record.value().getHarvested());
                        case RETAILERRECEIVED -> log("Handling typed retailer received ev %s", record.value().getRetailerReceived());
                        default -> log("Ignoring unknown event %s", record.value());
                    }

                });

        new Thread(consumer).start();

        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());
    }

    private Timestamp.Builder now() {
        return Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond());
    }

    private Urn.Builder randomUrn() {
        return Urn.newBuilder()
                .setIdentifier(UUID.randomUUID().toString());
    }

    private void log(String format, Object ... args){
        System.out.printf(format, args);
    }

}