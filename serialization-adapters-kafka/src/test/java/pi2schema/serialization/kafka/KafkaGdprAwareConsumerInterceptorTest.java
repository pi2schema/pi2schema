package pi2schema.serialization.kafka;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class KafkaGdprAwareConsumerInterceptorTest {

    @Test
    public void shouldThrowExceptionIfConfigurationsDoesNotContainMandatoryConfigs() {
        try (var interceptor = new KafkaGdprAwareConsumerInterceptor<>()) {
            Assertions.assertThatException().isThrownBy(() -> interceptor.configure(Collections.emptyMap()));
        }
    }
}
