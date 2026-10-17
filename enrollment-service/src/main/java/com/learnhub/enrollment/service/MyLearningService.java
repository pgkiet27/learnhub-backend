package com.learnhub.enrollment.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.enrollment.dto.response.EnrollmentResponse;
import com.learnhub.enrollment.entity.Enrollment;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MyLearningService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;

    /**
     * @param status "in_progress" | "completed" | "all" (defaults to "all")
     */
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> getMyLearning(UUID userId, String status,
                                                          Pageable pageable) {
        Page<Enrollment> page = switch (status == null ? "all" : status) {
            case "completed" -> enrollmentRepository
                    .findByUserIdAndIsCompletedOrderByLastAccessedAtDesc(userId, true, pageable);
            case "in_progress" -> enrollmentRepository
                    .findByUserIdAndIsCompletedOrderByLastAccessedAtDesc(userId, false, pageable);
            default -> enrollmentRepository
                    .findByUserIdOrderByLastAccessedAtDesc(userId, pageable);
        };

        return PageResponse.of(page.map(enrollmentService::toEnrollmentResponse));
    }
}