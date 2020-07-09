import avro.AvroEnvelop;
import avro.Harvested;
import avro.RetailerReceived;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaAvroSchemaRegistryTest {

    static final Map<String, String> KAFKA_PRODUCER_CONFIG = Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName()
    );

    static final Map<String, String> KAFKA_CONSUMER_CONFIG = Map.of(
            ConsumerConfig.GROUP_ID_CONFIG, "test-avro",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName(),
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true"
    );

    private static final String TOPIC_AVRO = "origins-avro";

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

        NewTopic avro = new NewTopic(TOPIC_AVRO, 1, (short) 1);
        adminClient.createTopics(List.of(avro));
    }

    @Test
    void testAvroEventSchema() throws InterruptedException {

        List<AvroEnvelop> events = List.of(
                AvroEnvelop.newBuilder()
                        .setCausation(UUID.randomUUID().toString())
                        .setCorrelation(UUID.randomUUID().toString())
                        .setData(
                                Harvested.newBuilder()
                                        .setPropertyId(UUID.randomUUID().toString())
                                        .setHarvestedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        .setHarvestedBy(UUID.randomUUID().toString())
                                        .build()
                        ).build(),
                AvroEnvelop.newBuilder()
                        .setCausation(UUID.randomUUID().toString())
                        .setCorrelation(UUID.randomUUID().toString())
                        .setData(
                                RetailerReceived.newBuilder()
                                        .setRetailerId(UUID.randomUUID().toString())
                                        .setReceivedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        .setReceivedBy(UUID.randomUUID().toString())
                                        .build()
                        ).build()
        );

        KafkaProducer<String, AvroEnvelop> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(TOPIC_AVRO, e.getCausation(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        KafkaConsumerRunner<String, AvroEnvelop> consumer = new KafkaConsumerRunner<>(
                consumerConfig,
                TOPIC_AVRO,
                record -> {
                    consumptionLatch.countDown();

                    // pattern matching using instanceof is not part of switch yet https://openjdk.java.net/jeps/8213076
                    // using jep-305 (experimental as of jdk 14)

                    Object event = record.value().getData();
                    if(event instanceof Harvested harvested){
                        System.out.println("Harvested event: " + harvested);
                    }
                    else if (event instanceof RetailerReceived received){
                        System.out.println("Retailer Received" + received);
                    }
                    else {
                        System.out.println("Unknown event " + event);
                    }

                });

        new Thread(consumer).start();

        producer.close(Duration.ofSeconds(30));
        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());
//        adminClient.deleteTopics(asList(newTopic.name()));
    }


    @Test
    void consumeAllMessagesFromTopic() throws ExecutionException, InterruptedException {

        Properties adminProperties = new Properties();
        adminProperties.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        AdminClient adminClient = AdminClient.create(adminProperties);

        Map<TopicPartition, OffsetAndMetadata> current = adminClient.listConsumerGroupOffsets("test-avro")
                .partitionsToOffsetAndMetadata()
                .get();

        log("current topic description: %s", current);

        TopicPartition topicPartition = new TopicPartition(TOPIC_AVRO, 0);
        adminClient.alterConsumerGroupOffsets("test-avro", Map.of(topicPartition, new OffsetAndMetadata(0L)));

        CountDownLatch consumptionLatch = new CountDownLatch((int) current.get(topicPartition).offset());
        KafkaConsumerRunner<String, AvroEnvelop> consumer = new KafkaConsumerRunner<>(
                consumerConfig,
                TOPIC_AVRO,
                record -> {
                    consumptionLatch.countDown();

                    log("Received event type %s \n", record.value().getData().getClass());

                    Object event = record.value().getData();
                    if(event instanceof Harvested harvested){
                        System.out.println("Harvested event: " + harvested);
                    }
                    else if (event instanceof RetailerReceived received){
                        System.out.println("Retailer Received" + received);
                    }
                    else {
                        System.out.println("Unknown event " + event);
                    }

                });

        new Thread(consumer).start();

        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());
    }


    private void log(String format, Object ... args){
        System.out.printf(format, args);
    }
}
