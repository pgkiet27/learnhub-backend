package com.learnhub.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;

@Builder
@Data
public class UserResponse {
    private String id;
    private String email;
    private String role;
    // Lombok strips the "is" prefix from boolean getters, so pin the JSON names explicitly
    // (on the generated getter, not the field, or both names get serialized)
    @Getter(onMethod_ = @JsonProperty("isEmailVerified"))
    private boolean isEmailVerified;
    @Getter(onMethod_ = @JsonProperty("isActive"))
    private boolean isActive;
    private Instant createdAt;
}
