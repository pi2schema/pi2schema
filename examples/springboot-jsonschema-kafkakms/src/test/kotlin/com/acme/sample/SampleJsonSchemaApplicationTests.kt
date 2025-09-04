package com.acme.sample

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class SampleJsonSchemaApplicationTests {

    @Test
    fun contextLoads() {
        // This test ensures that the Spring context loads successfully
        // with the JSON Schema configuration
    }
}
