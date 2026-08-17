package com.learnhub.identity.config;

import com.learnhub.identity.security.JwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

// using Spring Security OAuth2 Resource Server to verify JWT from Cognito and LearnHub
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // turn on method-level security annotations like @PreAuthorize, @PostAuthorize, etc.
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final JwtDecoder jwtDecoder; // CompositeJwtDecoder - handles both Cognito and LearnHub JWT

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Configure the application as an OAuth2 Resource Server - verify JWT from Cognito or LearnHub
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        // public endpoints that don't require authentication
                        .requestMatchers(
                                "api/v1/health",
                                "actuator/**",
                                "swagger-ui/**",
                                "v3/api-docs/**"
                        ).permitAll()
                        // auth endpoints - need Cognito authentication
                        .requestMatchers(HttpMethod.POST, "api/v1/auth/sync").authenticated()
                        .requestMatchers(HttpMethod.POST, "api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "api/v1/auth/logout").authenticated()
                        // admin only
                        .requestMatchers("api/v1/admin/**").hasRole("ADMIN")
                        // all other endpoints require authentication
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
