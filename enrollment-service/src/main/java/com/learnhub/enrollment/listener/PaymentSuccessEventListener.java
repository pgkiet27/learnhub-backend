package com.learnhub.enrollment.listener;

import com.learnhub.common.event.PaymentSuccessEvent;
import com.learnhub.enrollment.client.CourseServiceClient;
import com.learnhub.enrollment.dto.response.CourseInfoResponse;
import com.learnhub.enrollment.repository.EnrollmentRepository;
import com.learnhub.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessEventListener {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final CourseServiceClient courseServiceClient;

    @RabbitListener(queues = "enrollment.service.payment.success")
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        // Deduplication: RabbitMQ delivers messages "at-least-once" — the same payment
        // can be redelivered if the consumer didn't ack in time. Check before creating
        // to avoid a duplicate enrollment (unlike the upvote case in B3 Part 4, deduplication
        // here is MANDATORY since enrollments already has UNIQUE(user_id, course_id) —
        // a duplicate enroll would throw a DB error).
        if (enrollmentRepository.existsByUserIdAndCourseId(event.getUserId(), event.getCourseId())) {
            log.warn("Enrollment already exists for user {} course {}, skip (event may have already been processed)",
                    event.getUserId(), event.getCourseId());
            return;
        }

        CourseInfoResponse course = courseServiceClient.getCourse(event.getCourseId())
                .orElseThrow(() -> new IllegalStateException(
                        "Course " + event.getCourseId() + " not found while processing payment success"));

        enrollmentService.enrollCourse(event.getUserId(), course);
        log.info("Auto-enrolled user {} to course {} after payment success",
                event.getUserId(), event.getCourseId());
    }
}