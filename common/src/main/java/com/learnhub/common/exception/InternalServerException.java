package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if there is an error from server
public class InternalServerException extends LearnHubException {
    public InternalServerException(String errorCode, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message);
    }
}
