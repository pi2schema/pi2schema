package com.acme.sample

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean


fun main(args: Array<String>) {
    runApplication<SampleJsonSchemaApplication>(*args)
}


@SpringBootApplication
class SampleJsonSchemaApplication {

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
