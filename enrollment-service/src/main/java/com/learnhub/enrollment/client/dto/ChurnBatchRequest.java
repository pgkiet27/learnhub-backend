package com.learnhub.enrollment.client.dto;

import java.util.List;
import java.util.Map;

public record ChurnBatchRequest(List<Item> items) {

    /** Feature keys are the snake_case names the model was trained with. */
    public record Item(String id, Map<String, Double> features) {
    }
}
