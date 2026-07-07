package com.learnhub.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.learnhub.common.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Centralized handling of all exceptions in the system
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // handle LearnHubException and its subclass
    @ExceptionHandler(LearnHubException.class)
    public ResponseEntity<ApiResponse<Void>> handleLearnHubException(
            LearnHubException ex, WebRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Server error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Client error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }

        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // handle error Validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        // collect all field errors into Map
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        fieldErrors));
    }

    /**
     * handle request to URL is not exists (404).
     * Ex: browser call /favicon.ico for itself, or client call wrong URL.
     * <p>
     * Seperate to avoid log ERROR (only log DEBUG).
     * Because this is normal behavior, not server error
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex) {

        // Log DEBUG instead of ERROR — no need for alert
        log.debug("Resource not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "RESOURCE_NOT_FOUND",
                        "The requested resource does not exist"
                ));
    }

    // catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {
        log.error(
                "Unexpected error at [{}]: {}",
                request.getDescription(false),
                ex.getMessage(),
                ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please try again later."));
    }
}
