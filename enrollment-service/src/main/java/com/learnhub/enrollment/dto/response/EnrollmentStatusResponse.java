// dto/response/EnrollmentStatusResponse.java
package com.learnhub.enrollment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnrollmentStatusResponse {
    private boolean enrolled;
    private EnrollmentResponse enrollment;   // null if not enrolled
}