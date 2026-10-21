package com.learnhub.apigateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

@Configuration
public class ObservationConfig {

    // Health probes and Prometheus scrapes would otherwise flood Jaeger
    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext serverContext
                && serverContext.getCarrier().getPath().pathWithinApplication().value().startsWith("/actuator"));
    }
}
