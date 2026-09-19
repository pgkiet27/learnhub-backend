package com.learnhub.apigateway.filter;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Endpoints that can be called without a LearnHub access token.
 * Everything else must carry a valid token, otherwise the gateway answers 401.
 */
final class PublicRoutes {

    private record Rule(HttpMethod method, PathPattern pattern) {
    }

    private static final PathPatternParser PARSER = new PathPatternParser();

    private static final List<Rule> RULES = List.of(
            // Auth — /sync is verified by identity-service itself (Cognito token), /refresh uses X-Refresh-Token
            rule(HttpMethod.POST, "/api/v1/auth/sync"),
            rule(HttpMethod.POST, "/api/v1/auth/refresh"),

            // Course catalog — same list as course-service SecurityConfig
            rule(HttpMethod.GET, "/api/v1/categories"),
            rule(HttpMethod.GET, "/api/v1/courses"),
            rule(HttpMethod.GET, "/api/v1/courses/search"),
            rule(HttpMethod.GET, "/api/v1/courses/featured"),
            rule(HttpMethod.GET, "/api/v1/courses/category/**"),
            rule(HttpMethod.GET, "/api/v1/courses/*"),
            rule(HttpMethod.GET, "/api/v1/courses/*/sections"),

            // Public profile of another user (NOT /users/me/**, see isPublic)
            rule(HttpMethod.GET, "/api/v1/users/*/profile"),

            // Stripe calls this directly — authenticated by signature inside payment-service
            rule(HttpMethod.POST, "/api/v1/payments/webhook/stripe"),

            rule(HttpMethod.GET, "/api/v1/health"),
            rule(HttpMethod.GET, "/actuator/**")
    );

    private PublicRoutes() {
    }

    private static Rule rule(HttpMethod method, String pattern) {
        return new Rule(method, PARSER.parse(pattern));
    }

    static boolean isPublic(HttpMethod method, String path) {
        // "/users/me/profile" also matches "/users/*/profile" — it is private and must stay so
        if (path.startsWith("/api/v1/users/me")) {
            return false;
        }
        PathContainer container = PathContainer.parsePath(path);
        return RULES.stream()
                .anyMatch(r -> r.method().equals(method) && r.pattern().matches(container));
    }
}
