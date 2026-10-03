package com.learnhub.enrollment.controller;

import com.learnhub.common.dto.ApiResponse;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ConflictException;
import com.learnhub.enrollment.churn.ChurnDatasetExporter;
import com.learnhub.enrollment.churn.ChurnJobRunner;
import com.learnhub.enrollment.churn.ChurnPredictionService;
import com.learnhub.enrollment.dto.response.ChurnRunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Service-to-service / ops only: the API Gateway does not route /api/v1/internal/**.
 */
@Tag(name = "Internal", description = "APIs used internally between services")
@RestController
@RequestMapping("/api/v1/internal/churn")
@RequiredArgsConstructor
public class ChurnInternalController {

    private final ChurnJobRunner churnJobRunner;
    private final ChurnPredictionService churnPredictionService;
    private final ChurnDatasetExporter churnDatasetExporter;

    @Operation(summary = "Run the churn scoring job now (it also runs daily on a schedule)")
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<ChurnRunSummary>> run() {
        ChurnRunSummary summary = churnJobRunner.runIfNotRunning()
                .orElseThrow(() -> new ConflictException("CHURN_JOB_RUNNING",
                        "Churn scoring is already running (or just finished) on another replica"));
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Re-upload one date's churn features and labels to S3 (date in Asia/Ho_Chi_Minh)")
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<List<String>>> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (!churnDatasetExporter.isEnabled()) {
            throw new BadRequestException("CHURN_EXPORT_DISABLED", "Set CHURN_EXPORT_ENABLED=true to export the dataset");
        }
        return ResponseEntity.ok(ApiResponse.success(churnPredictionService.exportDataset(date)));
    }
}
