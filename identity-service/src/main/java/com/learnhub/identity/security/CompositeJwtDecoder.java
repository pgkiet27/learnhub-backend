package com.learnhub.identity.security;

import com.learnhub.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodes both Cognito JWT (RS256, verified via Cognito JWKS) and LearnHub JWT
 * (HMAC, verified via JwtService) — the two token types Identity Service accepts.
 *
 * Cognito JWT is used only by POST /auth/sync (before a user is synced to the DB).
 * LearnHub JWT is used by every other authenticated endpoint (/me, /logout, /admin/**).
 * Since these use different signing algorithms/keys, Spring Security's OAuth2 Resource
 * Server needs one JwtDecoder bean able to handle both — this class tries LearnHub
 * (fast, local, no network) first, then falls back to Cognito (network-bound JWKS).
 */
@Component
@RequiredArgsConstructor
public class CompositeJwtDecoder implements JwtDecoder {

    private final JwtService jwtService;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private NimbusJwtDecoder cognitoDecoder;

    @PostConstruct
    private void init() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        this.cognitoDecoder = decoder;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return decodeLearnHubToken(token);
        } catch (JwtException learnHubEx) {
            try {
                return cognitoDecoder.decode(token);
            } catch (JwtException cognitoEx) {
                throw new BadJwtException("Token is not a valid LearnHub or Cognito JWT", cognitoEx);
            }
        }
    }

    private Jwt decodeLearnHubToken(String token) {
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new BadJwtException("Invalid LearnHub JWT", e);
        }

        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now();
        Instant expiresAt = claims.getExpiration() != null
                ? claims.getExpiration().toInstant()
                : issuedAt.plusSeconds(1);

        Map<String, Object> claimsMap = new HashMap<>(claims);

        return Jwt.withTokenValue(token)
                .headers(h -> h.put("alg", "HS256"))
                .claims(c -> c.putAll(claimsMap))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
