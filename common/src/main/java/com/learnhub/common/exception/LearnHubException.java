package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

// base exception for project, all custom exceptions need to extends this class
@Getter
public class LearnHubException extends RuntimeException {
    private final HttpStatus status; // http status code
    private final String errorCode; // error code for FE handle

    public LearnHubException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
