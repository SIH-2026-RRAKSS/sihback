package com.sih.dataservice.policy.controller;

import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.policy.dto.PolicyEvaluationResultDto;
import com.sih.dataservice.policy.service.PolicyEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/policy")
@Tag(name = "Policy", description = "Threshold tuning and model policy evaluation")
public class PolicyController {

    private final PolicyEvaluationService policyEvaluationService;

    public PolicyController(PolicyEvaluationService policyEvaluationService) {
        this.policyEvaluationService = policyEvaluationService;
    }

    @Operation(summary = "Perform threshold sweep over labeled ground truth cases to compute precision, recall, and F1 curve")
    @GetMapping({"/threshold-sweep", "/tune"})
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<PolicyEvaluationResultDto>> getThresholdSweep() {
        PolicyEvaluationResultDto result = policyEvaluationService.evaluateThresholdSweep();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Tune policy thresholds over labeled cases")
    @PostMapping("/tune")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<PolicyEvaluationResultDto>> tunePolicy() {
        PolicyEvaluationResultDto result = policyEvaluationService.evaluateThresholdSweep();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
