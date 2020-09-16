package pi2schema.crypto.providers.kafkakms;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class KafkaTestUtils {

    public static void createTopics(Map<String, Object> configs, String... topics) {
        var newTopics =
                Arrays.stream(topics)
                        .map(topic -> new NewTopic(topic, 1, (short) 1))
                        .collect(Collectors.toList());

        try (AdminClient admin = AdminClient.create(configs)) {
            admin.createTopics(newTopics);
        }
    }
}
