package com.acme.sample

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.ErrorHandler
import org.springframework.kafka.listener.SeekToCurrentErrorHandler
import org.springframework.kafka.listener.SeekUtils
import org.springframework.util.backoff.FixedBackOff


fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}


@SpringBootApplication
class SampleApplication {

    @Bean
    fun kafkaErrorHandler(): ErrorHandler = SeekToCurrentErrorHandler(
            FixedBackOff(1500, SeekUtils.DEFAULT_MAX_FAILURES.toLong()) // the default is to retry immediately
    )

    @Bean
    fun farmerTopic(): NewTopic = NewTopic(
            "farmer",
            3,
            1)
}