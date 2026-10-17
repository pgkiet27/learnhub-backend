package com.learnhub.course.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.course.dto.request.CreateSectionRequest;
import com.learnhub.course.dto.response.SectionResponse;
import com.learnhub.course.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Sections", description = "Quản lý chương/phần của khóa học")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    @Operation(summary = "Lấy tất cả sections của khóa học")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SectionResponse>>> getSections(
            @PathVariable UUID courseId) {
        return ResponseEntity.ok(
                ApiResponse.success(sectionService.getSectionsByCourse(courseId), "OK"));
    }

    @Operation(summary = "Tạo section mới")
    @PostMapping
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID courseId,
            @RequestBody @Valid CreateSectionRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        SectionResponse section = sectionService.createSection(courseId, request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(section, "Tạo section thành công"));
    }

    @Operation(summary = "Cập nhật section")
    @PutMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<SectionResponse>> updateSection(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @RequestBody @Valid CreateSectionRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {

        return ResponseEntity.ok(ApiResponse.success(
                sectionService.updateSection(courseId, sectionId, request, instructorId),
                "Cập nhật thành công"));
    }

    @Operation(summary = "Xóa section")
    @DeleteMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @RequestHeader("X-User-Id") UUID instructorId) {

        sectionService.deleteSection(courseId, sectionId, instructorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa section thành công"));
    }
}