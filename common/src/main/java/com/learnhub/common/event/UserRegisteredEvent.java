package com.learnhub.common.event;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private UUID userId;
    private String email;
    private String fullName;
    private String role;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
