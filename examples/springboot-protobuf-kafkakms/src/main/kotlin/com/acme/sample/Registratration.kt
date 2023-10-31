package com.acme.sample

import com.acme.FarmerRegisteredEventOuterClass
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent
import org.apache.kafka.clients.admin.NewTopic
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
@Profile("registration")
class ApplicationRouter(private val handler: FarmerRegistrationHandler) {

    @Bean
    fun registrationRouter(): RouterFunction<ServerResponse> = router {
        POST(
                "/api/v1/farmers",
                accept(MediaType.APPLICATION_JSON),
                handler::register
        )
    }
}


data class FarmerRegistrationRequest(
        val name: String,
        val email: String,
        val phone: String)


@Component
@Profile("registration")
class FarmerRegistrationHandler(
        private val kafkaTemplate: KafkaTemplate<String, FarmerRegisteredEvent>,
        private val farmerTopic: NewTopic) {

    fun register(request: ServerRequest): Mono<ServerResponse> {

        return request.bodyToMono(FarmerRegistrationRequest::class.java)
                .map { command ->
                    FarmerRegisteredEvent.newBuilder()
                            .setUuid(UUID.randomUUID().toString())
                            .setContactInfo(FarmerRegisteredEventOuterClass.ContactInfo.newBuilder()
                                    .setName(command.name)
                                    .setEmail(command.email)
                                    .setPhoneNumber(command.phone)
                            )
                            .build()
                }
                .flatMap { event ->
                    Mono.fromFuture(
                            kafkaTemplate.send(farmerTopic.name(), event.uuid, event)
                    )
                }
                .flatMap {
                    ServerResponse.ok().build()
                }
                .switchIfEmpty(ServerResponse.badRequest().build())
    }
}