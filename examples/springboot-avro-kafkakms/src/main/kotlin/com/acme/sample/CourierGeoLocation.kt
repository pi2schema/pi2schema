package com.acme.sample

import com.acme.CourierLocationEvent
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.util.*

@Configuration
@Profile("geoLocation")
class ApplicationRouter(private val handler: CourierLocationHandler) {

    @Bean
    fun geoLocationRouter(): RouterFunction<ServerResponse> = router {
        POST(
                "/api/v1/geoLocation",
                accept(MediaType.APPLICATION_JSON),
                handler::geoLocation
        )
    }
}


data class CourierLocationRequest(
        val name: String,
        val geoLocation: String)


@Component
@Profile("geoLocation")
class CourierLocationHandler(
    private val kafkaTemplate: KafkaTemplate<String, CourierLocationEvent>,
    private val geoLocationTopic: NewTopic) {

    val log: Logger = LoggerFactory.getLogger(PreProcessorListener::class.java)

    fun geoLocation(request: ServerRequest): Mono<ServerResponse> {

        return request.bodyToMono(CourierLocationRequest::class.java)
                .map { command ->
                    CourierLocationEvent.newBuilder()
                            .setCourierId(Random().nextInt(99999).toString())
                            .setName(command.name)
                            .setGeoLocation(command.geoLocation)
                            .build()
                }
                .doOnNext { event -> log.info("Publishing event {}", event)}
                .flatMap { event ->
                    Mono.fromFuture(
                            kafkaTemplate.send(geoLocationTopic.name(), event.courierId, event)
                    )
                }
                .flatMap {
                    ServerResponse.ok().build()
                }
                .switchIfEmpty(ServerResponse.badRequest().build())
    }
}