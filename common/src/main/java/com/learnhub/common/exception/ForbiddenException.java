package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if user was signed in but does not have permission to do action
public class ForbiddenException extends LearnHubException {
    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
