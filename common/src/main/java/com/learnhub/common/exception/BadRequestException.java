package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if request data is not valid (in business logic way)
public class BadRequestException extends LearnHubException {
    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
