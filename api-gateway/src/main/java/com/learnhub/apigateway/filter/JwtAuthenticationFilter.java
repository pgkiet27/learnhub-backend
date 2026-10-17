package com.learnhub.apigateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Trust boundary of the system: verifies the LearnHub access token (HS256, signed by identity-service
 * with the shared JWT_SECRET) and tells the downstream services who the caller is through
 * X-User-Id / X-User-Role.
 *
 * Any X-User-Id / X-User-Role sent by the client is removed first, otherwise anyone could
 * impersonate any user just by setting the header.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String BEARER_PREFIX = "Bearer ";

    private record Identity(String userId, String role) {
    }

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${gateway.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        Optional<Identity> identity = extractIdentity(request);
        boolean isPublic = PublicRoutes.isPublic(request.getMethod(), request.getPath().value());

        if (identity.isEmpty() && !isPublic) {
            return unauthorized(exchange, "Missing or invalid access token");
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    identity.ifPresent(id -> {
                        headers.set(USER_ID_HEADER, id.userId());
                        headers.set(USER_ROLE_HEADER, id.role());
                    });
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Optional<Identity> extractIdentity(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();

            // refresh tokens must not be usable as access tokens
            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new Identity(userId, role.toLowerCase(Locale.ROOT)));
        } catch (JwtException | IllegalArgumentException e) {
            // e.g. a Cognito token on /auth/sync — identity-service verifies that one itself
            log.debug("Access token rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    // run before the routing filters so the mutated headers are the ones forwarded
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
