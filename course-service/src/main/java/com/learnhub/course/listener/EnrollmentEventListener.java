package com.learnhub.course.listener;

import com.learnhub.common.event.EnrollmentCreatedEvent;
import com.learnhub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventListener {

    private final CourseRepository courseRepository;

    @RabbitListener(queues = "course.service.enrollment.created")
    @Transactional
    public void handleEnrollmentCreated(EnrollmentCreatedEvent event) {
        courseRepository.findById(event.getCourseId()).ifPresent(course -> {
            course.setTotalStudents(course.getTotalStudents() + 1);
            courseRepository.save(course);
            log.info("Updated totalStudents for course: {}", event.getCourseId());
        });
    }
}
