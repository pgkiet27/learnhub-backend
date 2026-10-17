package com.learnhub.enrollment.client.dto;

/**
 * The {success, message, data} envelope returned by other LearnHub services.
 */
public record ServiceResponse<T>(boolean success, String message, T data) {
}
