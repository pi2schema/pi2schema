import br.com.oprodutor.origins.proto.DomainEventsV1.Harvested;
import br.com.oprodutor.origins.proto.DomainEventsV1.RetailerReceived;
import br.com.oprodutor.origins.proto.EnvelopUsingAnyV1.EnvelopAny;
import br.com.oprodutor.origins.proto.EnvelopUsingOneOfV1.EnvelopOneOf;
import br.com.oprodutor.origins.proto.UrnOuterClass.Urn;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
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
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class.getName()
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
    public void testOneOf() throws InterruptedException {
        NewTopic newTopic = new NewTopic("origins-one-of", 1, (short) 1);
        adminClient.createTopics(List.of(newTopic));

        List<EnvelopOneOf> events = List.of(EnvelopOneOf.newBuilder()
                        .setHarvested(randomHarvestedEvent())
                        .build(),
                EnvelopOneOf.newBuilder()
                        .setRetailerReceived(randomRetailerReceived())
                        .build());

        KafkaProducer<String, EnvelopOneOf> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(newTopic.name(), e.getCausation().getIdentifier(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        ConcurrentKafkaConsumer<String, EnvelopOneOf> consumer = new ConcurrentKafkaConsumer<>(
                consumerConfig,
                newTopic.name(),
                record -> {
                    Object event = switch (record.value().getEventCase()) {
                        case HARVESTED -> record.value().getHarvested();
                        case RETAILERRECEIVED -> record.value().getRetailerReceived();
                        default -> Optional.empty();
                    };

                    System.out.println("Simply consuming one of events" + event);
                    consumptionLatch.countDown();
                });

        new Thread(consumer).start();

        producer.close(Duration.ofSeconds(30));
        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());

//        adminClient.deleteTopics(asList(newTopic.name()));
    }

    @Test
    void testAny() throws InterruptedException {

        NewTopic newTopic = new NewTopic("origins-any", 1, (short) 1);
        adminClient.createTopics(List.of(newTopic));

        List<EnvelopAny> events = List.of(
                EnvelopAny.newBuilder()
                        .setCausation(randomCausation())
                        .setEvent(Any.pack(randomHarvestedEvent().build()))
                        .build(),
                EnvelopAny.newBuilder()
                        .setEvent(Any.pack(randomRetailerReceived().build()))
                        .build());

        KafkaProducer<String, EnvelopAny> producer = new KafkaProducer<>(producerConfig);
        events.stream()
                .map(e -> new ProducerRecord<>(newTopic.name(), e.getCausation().getIdentifier(), e))
                .forEach(producer::send);
        producer.close();

        CountDownLatch consumptionLatch = new CountDownLatch(events.size());
        ConcurrentKafkaConsumer<String, EnvelopAny> consumer = new ConcurrentKafkaConsumer<>(
                consumerConfig,
                newTopic.name(),
                record -> {
                    consumptionLatch.countDown();

                    try {
                        Any event = record.value().getEvent();
                        if (event.is(Harvested.class)) {
                            System.out.println(event.unpack(Harvested.class));
                        }
                        else if (event.is(RetailerReceived.class)){
                            System.out.println(event.unpack(RetailerReceived.class));
                        }
                        else {
                            System.out.print("Unexpected event " + event);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                });

        new Thread(consumer).start();

        consumptionLatch.await(30, TimeUnit.SECONDS);
        consumer.shutdown();

        assertEquals(0, consumptionLatch.getCount());

    }


    private Urn.Builder randomCausation() {
        return Urn.newBuilder()
                .setIdentifier(UUID.randomUUID().toString());
    }

    private RetailerReceived.Builder randomRetailerReceived() {
        return RetailerReceived.newBuilder()
                .setReceivedAt(
                        Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                )
                .setReceivedBy(
                        Urn.newBuilder()
                                .setIdentifier(UUID.randomUUID().toString())
                );
    }

    private Harvested.Builder randomHarvestedEvent() {
        return Harvested.newBuilder()
                .setHarvestedAt(
                        Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                )
                .setHarvestedBy(
                        Urn.newBuilder()
                                .setIdentifier(UUID.randomUUID().toString())
                );
    }


}