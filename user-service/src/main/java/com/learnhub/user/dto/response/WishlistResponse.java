package com.learnhub.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WishlistResponse {
    private UUID courseId;
    private Instant addedAt;
}
