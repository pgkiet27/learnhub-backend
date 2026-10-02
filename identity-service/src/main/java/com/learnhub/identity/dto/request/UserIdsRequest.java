package com.learnhub.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserIdsRequest {
    @NotNull
    @Size(max = 1000)
    private List<UUID> userIds;
}
