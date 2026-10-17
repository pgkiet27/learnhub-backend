package com.learnhub.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Builds the Spring Security Authentication from the X-User-Id / X-User-Role headers that the
 * API Gateway sets after it has verified the access token.
 *
 * These services do not verify JWTs themselves, so they must only be reachable through the gateway
 * (network policy / ClusterIP). If a client could call a service directly, it could forge these headers.
 *
 * Role "instructor" becomes authority ROLE_instructor, so hasRole("instructor") works.
 */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);

        if (userId != null && !userId.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = request.getHeader(USER_ROLE_HEADER);
            List<SimpleGrantedAuthority> authorities = (role == null || role.isBlank())
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role.trim().toLowerCase(Locale.ROOT)));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, authorities));
        }

        filterChain.doFilter(request, response);
    }
}
