package com.learnhub.enrollment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public — no token required
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Every other endpoint (enroll, progress, my-learning...) requires
                        // authentication — no role-based split needed since ownership is
                        // matched directly via X-User-Id inside the Service layer
                        // (findByUserIdAndCourseId); this service has no notion of
                        // "admin/instructor" roles
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}