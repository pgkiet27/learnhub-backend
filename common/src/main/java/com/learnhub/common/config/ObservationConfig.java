package com.learnhub.common.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservationConfig {

    // Health probes and Prometheus scrapes would otherwise flood Jaeger
    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                return !serverContext.getCarrier().getRequestURI().startsWith("/actuator");
            }
            // Security spans of an excluded request would otherwise show up as their own root traces
            return !(name.startsWith("spring.security.") && context.getParentObservation() == null);
        };
    }
}
