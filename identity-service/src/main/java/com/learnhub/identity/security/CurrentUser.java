package com.learnhub.identity.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

// utility class to get the current authenticated user information from Spring Security context
public class CurrentUser {
    // get cognito_sub (subject) from the user which is requested in the current request
    public static String getCognitoSub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt.getSubject(); // "sub" claim in JWT is the Cognito sub
    }

    // get email from JWT claims
    public static String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt.getClaimAsString("email"); // "email" claim in JWT is the Cognito email
    }

    // get all claims from JWT
    public static Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt;
    }
}
