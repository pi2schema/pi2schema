package com.acme.sample

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class SampleApplication {

    @Bean
    fun createKafkaTopic(): NewTopic = NewTopic("farmer", 3, 1)

}

fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}
