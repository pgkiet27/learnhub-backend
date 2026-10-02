package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.enrollment.dto.response.CourseStudentsResponse;
import com.learnhub.enrollment.service.CourseStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Course Students", description = "Students of a course with churn risk, for its instructor")
@RestController
@RequestMapping("/api/v1/enrollments/courses/{courseId}")
@RequiredArgsConstructor
public class CourseStudentController {

    private final CourseStudentService courseStudentService;

    @Operation(summary = "List students of the course, highest churn risk first")
    @GetMapping("/students")
    public ResponseEntity<ApiResponse<CourseStudentsResponse>> getStudents(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "student") String role,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                courseStudentService.getStudents(courseId, userId, role, riskLevel, page, size)));
    }

    @Operation(summary = "Email a learning reminder to high-risk students not reminded in the last 7 days")
    @PostMapping("/churn-reminders")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sendChurnReminders(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "student") String role) {
        int queued = courseStudentService.sendChurnReminders(courseId, userId, role);
        return ResponseEntity.ok(ApiResponse.success(Map.of("queued", queued), "Reminders queued"));
    }
}
