package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.dto.PageResponse;
import com.learnhub.course.dto.request.CreateCourseRequest;
import com.learnhub.course.dto.request.PresignedUrlRequest;
import com.learnhub.course.dto.request.UpdateCourseRequest;
import com.learnhub.course.dto.response.CourseResponse;
import com.learnhub.course.service.CourseService;
import com.learnhub.course.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Courses", description = "Quản lý khóa học")
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final S3Service s3Service;

    // PUBLIC endpoints

    @Operation(summary = "Lấy danh sách khóa học published")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getPublishedCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageResponse<CourseResponse> courses = courseService
                .getPublishedCourses(PageRequest.of(page, size, sort));

        return ResponseEntity.ok(ApiResponse.success(courses, "OK"));
    }

    @Operation(summary = "Lấy chi tiết khóa học theo slug")
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourseBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(courseService.getCourseBySlug(slug), "OK"));
    }

    // INSTRUCTOR endpoints

    @Operation(summary = "Tạo khóa học mới (Instructor)")
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @RequestBody @Valid CreateCourseRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        CourseResponse course = courseService.createCourse(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(course, "Tạo khóa học thành công"));
    }

    @Operation(summary = "Lấy danh sách khóa học của Instructor")
    @GetMapping("/instructor/my-courses")
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getInstructorCourses(
            @RequestHeader("X-User-Id") UUID instructorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<CourseResponse> courses = courseService
                .getInstructorCourses(instructorId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(courses, "OK"));
    }

    @Operation(summary = "Cập nhật khóa học (Instructor)")
    @PutMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID courseId,
            @RequestBody @Valid UpdateCourseRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        CourseResponse course = courseService.updateCourse(courseId, request, instructorId);
        return ResponseEntity.ok(ApiResponse.success(course, "Cập nhật thành công"));
    }

    @Operation(summary = "Xóa khóa học draft (Instructor)")
    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID instructorId) {

        courseService.deleteCourse(courseId, instructorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa khóa học thành công"));
    }

    // S3 Upload

    @Operation(summary = "Lấy presigned URL để upload file lên S3")
    @PostMapping("/{courseId}/upload-url")
    public ResponseEntity<ApiResponse<S3Service.PresignedUploadResponse>> getUploadUrl(
            @PathVariable UUID courseId,
            @RequestBody @Valid PresignedUrlRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        // Choose the S3 folder based on file type
        String folder = switch (request.getFileType()) {
            case "thumbnail" -> "thumbnails";
            case "document" -> "documents";
            default -> "videos";
        };

        S3Service.PresignedUploadResponse response =
                s3Service.generatePresignedUploadUrl(
                        folder + "/" + courseId,
                        request.getFileName(),
                        request.getContentType());

        return ResponseEntity.ok(
                ApiResponse.success(response, "Presigned URL tạo thành công"));
    }
}