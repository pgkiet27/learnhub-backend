package com.learnhub.common.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // remove null value out of json
public class ApiResponse<T> {
    private boolean success;
    private String message;

    // contain data if success is true
    private T data;

    // contain error if success is false
    private ErrorDetail error;

    // time when response is created
    @Builder.Default
    private Instant timestamp = Instant.now();

    // success with data and customize message
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // success with default message "Success"
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Success");
    }

    // success and no data (used for Delete)
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    // error with code, message, and details
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }

    // simple error with no details
    public static <T> ApiResponse<T> error(String code, String message) {
        return error(code, message, null);
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code; // code error ("USER_NOT_FOUND", "INVALID_TOKEN")
        private String message;
        private Object details; // more information, typically validation errors
    }
}
