package com.acme.sample

import com.acme.model.FarmerRegisteredEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Profile("newsletter")
@EnableKafka
@Component
class NewsletterEventListener {

    val log: Logger = LoggerFactory.getLogger(NewsletterEventListener::class.java)

    @KafkaListener(topics = ["farmer"], groupId = "newsletter")
    fun onFarmerRegisteredEvent(event: FarmerRegisteredEvent) {
        log.debug("Received Event: {}", event)

        log.info(
                "Sending welcome to the newsletter for the user {} and email {}",
                event.name,
                event.email)
    }
}
