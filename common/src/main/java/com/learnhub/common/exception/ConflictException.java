package com.learnhub.common.exception;

import org.springframework.http.HttpStatus;

// throw if there is a conflict about data
public class ConflictException extends LearnHubException {
    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
