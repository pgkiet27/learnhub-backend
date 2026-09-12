package com.learnhub.identity.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Convert Cognito JWT to Spring Security Authentication Token
 *
 * Cognito JWT have claimed roles in "cognito:groups" which is a list of groups the user belongs to.
 * We can map these groups to Spring Security authorities.
 * This converter will extract the roles from the JWT and convert to GrantedAuthority list for Spring Security.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Cognito save role in custom attribute "custom:role" or groups
        // we will get the role from DB after sync user
        // for now, we will just return ROLE_USER for all authenticated users
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
