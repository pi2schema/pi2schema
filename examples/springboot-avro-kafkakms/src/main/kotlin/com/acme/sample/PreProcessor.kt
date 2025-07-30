package com.acme.sample

import com.acme.CourierLocationEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Profile("preProcessor")
@EnableKafka
@Component
class PreProcessorListener {

    val log: Logger = LoggerFactory.getLogger(PreProcessorListener::class.java)

    @KafkaListener(topics = ["geoLocation"], groupId = "preProcessor")
    fun onGeoLocationEvent(event: CourierLocationEvent) {
        log.info("Processing Event: {}", event)
    }
}