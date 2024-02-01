package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public class KafkaGdprAwareInterceptorIntegrationTest {

}
