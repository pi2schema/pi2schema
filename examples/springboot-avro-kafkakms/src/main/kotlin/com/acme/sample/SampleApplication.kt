package com.acme.sample

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.SeekUtils
import org.springframework.util.backoff.FixedBackOff


fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}


@SpringBootApplication
class SampleApplication {

    @Bean
    fun kafkaErrorHandler() = DefaultErrorHandler(
        { _, _ -> },
        FixedBackOff(1500, SeekUtils.DEFAULT_MAX_FAILURES.toLong())
    )


    @Bean
    fun farmerTopic(): NewTopic = NewTopic(
            "farmer",
            3,
            1)

    @Bean
    fun pi2schemaCommandsTopic(): NewTopic = NewTopic(
            "pi2schema.kms.commands",
            3,
            1)

    @Bean
    fun pi2schemaLocalStateTopic(): NewTopic = NewTopic(
            "pi2schema.kms-local-changelog",
            3,
            1)
            .configs(
                    mapOf(
                            "cleanup.policy" to "compact"))

}