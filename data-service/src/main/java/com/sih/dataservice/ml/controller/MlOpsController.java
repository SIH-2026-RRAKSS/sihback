package com.sih.dataservice.ml.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.ml.dto.ModelVersionDetailDto;
import com.sih.dataservice.ml.dto.RetrainRequestDto;
import com.sih.dataservice.ml.dto.RetrainingDashboardDto;
import com.sih.dataservice.ml.dto.TrainingSnapshotDto;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.ml.service.RetrainingService;
import com.sih.dataservice.ml.service.SnapshotExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for ML-Ops retraining pipelines, de-identified snapshots, model evaluation, and promotion (FR-RTR-1..6).
 */
@Tag(name = "ML-Ops", description = "Endpoints for managing retraining snapshots, training candidate models, and manual model promotions")
@RestController
@RequestMapping("/ml-ops")
public class MlOpsController {

    private final SnapshotExportService snapshotExportService;
    private final RetrainingService retrainingService;
    private final TrainingSnapshotRepository snapshotRepository;

    public MlOpsController(
            SnapshotExportService snapshotExportService,
            RetrainingService retrainingService,
            TrainingSnapshotRepository snapshotRepository) {
        this.snapshotExportService = snapshotExportService;
        this.retrainingService = retrainingService;
        this.snapshotRepository = snapshotRepository;
    }

    @Operation(summary = "Export a new immutable, de-identified training snapshot of closed cases (FR-RTR-2)")
    @PostMapping("/snapshots")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TrainingSnapshotDto>> exportSnapshot(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        TrainingSnapshot snapshot = snapshotExportService.exportSnapshot(principal, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(TrainingSnapshotDto.fromEntity(snapshot), "Training snapshot generated successfully"));
    }

    @Operation(summary = "List all training snapshots")
    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<TrainingSnapshotDto>>> listSnapshots() {
        List<TrainingSnapshotDto> snapshots = snapshotRepository.findAllByOrderByVersionDesc()
                .stream()
                .map(TrainingSnapshotDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(snapshots));
    }

    @Operation(summary = "Get snapshot details by ID")
    @GetMapping("/snapshots/{id}")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TrainingSnapshotDto>> getSnapshot(@PathVariable("id") UUID id) {
        TrainingSnapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Training snapshot not found"));
        return ResponseEntity.ok(ApiResponse.ok(TrainingSnapshotDto.fromEntity(snapshot)));
    }

    @Operation(summary = "Trigger retraining of a candidate model from a training snapshot (FR-RTR-5)")
    @PostMapping("/retrain")
    @PreAuthorize("hasRole('CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<ModelVersionDetailDto>> retrainModel(
            @Valid @RequestBody RetrainRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        ModelVersionDetailDto result = retrainingService.triggerRetraining(requestDto, principal, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(result, "Retraining initiated and candidate model registered"));
    }

    @Operation(summary = "List registered models and candidate models")
    @GetMapping("/models")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN', 'POLICE')")
    public ResponseEntity<ApiResponse<List<ModelVersionDetailDto>>> listModels() {
        List<ModelVersionDetailDto> models = retrainingService.listModels();
        return ResponseEntity.ok(ApiResponse.ok(models));
    }

    @Operation(summary = "Promote a candidate model version to ACTIVE, retiring the previous active version (FR-RTR-5, FR-RTR-6)")
    @PostMapping("/models/{id}/promote")
    @PreAuthorize("hasRole('CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<ModelVersionDetailDto>> promoteCandidate(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        ModelVersionDetailDto promoted = retrainingService.promoteCandidate(id, principal, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(promoted, "Model version promoted to ACTIVE successfully"));
    }

    @Operation(summary = "Retrieve retraining dashboard metrics including new labeled cases since latest snapshot (FR-RTR-3)")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RetrainingDashboardDto>> getDashboard() {
        RetrainingDashboardDto dashboard = retrainingService.getDashboard();
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }
}
