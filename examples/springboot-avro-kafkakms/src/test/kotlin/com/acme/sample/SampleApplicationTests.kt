package com.acme.sample

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.redpanda.RedpandaContainer


@SpringBootTest
@Testcontainers
class SampleApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        val redpandaContainer: RedpandaContainer =
            RedpandaContainer("redpandadata/redpanda:v25.2.10")
    }


    @Test
    fun contextLoads() {
    }

}