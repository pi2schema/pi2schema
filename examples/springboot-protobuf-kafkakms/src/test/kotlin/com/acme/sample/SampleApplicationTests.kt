//package com.acme.sample
//
//import org.junit.jupiter.api.Test
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.test.context.DynamicPropertyRegistry
//import org.springframework.test.context.DynamicPropertySource
//import org.testcontainers.containers.KafkaContainer
//import org.testcontainers.containers.Network
//import org.testcontainers.junit.jupiter.Container
//import org.testcontainers.junit.jupiter.Testcontainers
//
//
//@SpringBootTest
//@Testcontainers
//class SampleApplicationTests {
//
//    companion object {
//        @Container
//        val kafka = KafkaContainer().withNetwork(Network.SHARED)
//
//        @JvmStatic
//        @DynamicPropertySource
//        fun kafka(registry: DynamicPropertyRegistry) {
//            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
//        }
//    }
//
//
//    @Test
//    fun contextLoads() {
//    }
//
//}