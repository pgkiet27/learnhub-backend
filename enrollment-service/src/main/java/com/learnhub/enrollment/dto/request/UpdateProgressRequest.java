// dto/request/UpdateProgressRequest.java
package com.learnhub.enrollment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProgressRequest {
    @NotNull
    @Min(0)
    private Integer watchDurationSec;

    @NotNull
    @Min(0)
    private Integer lastPositionSec;

    // Optional — sent by the Frontend for video lessons (already available from the loaded lesson data).
    // Not sent (null) for document/text lessons
    private Integer videoDurationSec;
}