package com.learnhub.course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/courses",
                                "/api/v1/courses/search",
                                "/api/v1/courses/featured",
                                "/api/v1/courses/category/**",
                                "/api/v1/courses/{slug}",
                                "/api/v1/courses/*/sections",
                                "/api/v1/categories",
                                "/api/v1/health",
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Instructor — requires JWT (X-User-Id header injected by the API Gateway)
                        .requestMatchers("/api/v1/courses/instructor/**").hasRole("instructor")
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses").hasRole("instructor")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**").hasRole("instructor")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**").hasRole("instructor")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/courses/**").hasRole("instructor")
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/submit").hasRole("instructor")
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/unpublish").hasRole("instructor")
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/upload-url").hasRole("instructor")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/courses/*/sections",
                                "/api/v1/courses/*/sections/*/lessons",
                                "/api/v1/courses/*/sections/*/lessons/*/publish"
                        ).hasRole("instructor")

                        // Admin only
                        .requestMatchers("/api/v1/admin/**").hasRole("admin")

                        .anyRequest().authenticated()
                );

        return http.build();
    }
}