import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ConcurrentKafkaConsumer<K, V> implements Runnable {
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    private final KafkaConsumer<K, V> consumer;
    private final Consumer<ConsumerRecord<K, V>> handler;

    public ConcurrentKafkaConsumer(Properties consumerConfig, String topic, Consumer<ConsumerRecord<K, V>> handler) {
        this.handler = handler;
        this.consumer = new KafkaConsumer<>(consumerConfig);
        this.consumer.subscribe(List.of(topic));
    }

    public void run() {
        try (consumer) {
            while (!interrupted.get()) {
                consumer.poll(Duration.ofMillis(1000))
                        .forEach(this.handler);
            }
        }
    }

    // Shutdown hook which can be called from a separate thread
    public void shutdown() {
        interrupted.set(true);
        consumer.wakeup();
    }
}
