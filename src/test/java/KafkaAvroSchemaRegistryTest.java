import avro.AvroEnvelop;
import avro.Harvested;
import avro.RetailerReceived;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaAvroSchemaRegistryTest {

    static final Map<String, String> KAFKA_PRODUCER_CONFIG = Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName()
    );

    static final Map<String, String> KAFKA_CONSUMER_CONFIG = Map.of(
            ConsumerConfig.GROUP_ID_CONFIG, "test-proto",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName(),
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true"
    );

    private AdminClient adminClient;
    private Properties consumerConfig;
    private Properties producerConfig;

    @BeforeEach
    public void setUp() {
        producerConfig = new Properties();
        producerConfig.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        adminClient = AdminClient.create(producerConfig);

        producerConfig.putAll(KAFKA_PRODUCER_CONFIG);

        consumerConfig = new Properties();
        consumerConfig.putAll(KafkaSimpleConfig.KAFKA_INFRA_CONFIG);
        consumerConfig.putAll(KAFKA_CONSUMER_CONFIG);
    }

    @Test
    void testAvroEventSchema() throws InterruptedException {
        NewTopic newTopic = new NewTopic("origins-avro", 1, (short) 1);
        adminClient.createTopics(List.of(newTopic));

        List<AvroEnvelop> events = List.of(
                AvroEnvelop.newBuilder()
                        .setCausation(UUID.randomUUID().toString())
                        .setCorrelation(UUID.randomUUID().toString())
                        .setData(
                                Harvested.newBuilder()
                                        .setHarvestedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        .setHarvestedBy(UUID.randomUUID().toString())
                                        .build()
                        ).build(),
                AvroEnvelop.newBuilder()
                        .setCausation(UUID.randomUUID().toString())
                        .setCorrelation(UUID.randomUUID().toString())
                        .setData(
                                RetailerReceived.newBuilder()
                                        .setReceivedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        .setReceivedBy(UUID.randomUUID().toString())
                                        .build()
                        ).build()
        );

        KafkaProducer<String, AvroEnvelop> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(newTopic.name(), e.getCausation(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        ConcurrentKafkaConsumer<String, AvroEnvelop> consumer = new ConcurrentKafkaConsumer<>(
                consumerConfig,
                newTopic.name(),
                record -> {
                    consumptionLatch.countDown();

                    // pattern matching using instanceof is not part of switch yet https://openjdk.java.net/jeps/8213076
                    // using jep-305 (experimental as of jdk 14)

                    Object event = record.value().getData();
                    if(event instanceof Harvested harvested){
                        System.out.println("harvested: " + harvested);
                    }
                    else if (event instanceof RetailerReceived received){
                        System.out.println("received" + received);
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
}
