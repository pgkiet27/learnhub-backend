package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if the system cannot find resources in database
public class ResourceNotFoundException extends LearnHubException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
