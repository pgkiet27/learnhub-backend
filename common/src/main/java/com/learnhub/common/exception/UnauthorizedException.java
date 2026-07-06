package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if user is not sign in or token is invalid
public class UnauthorizedException extends LearnHubException {
    public UnauthorizedException(String errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
}
