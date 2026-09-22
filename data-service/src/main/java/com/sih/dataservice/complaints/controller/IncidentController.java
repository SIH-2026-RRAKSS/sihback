package com.sih.dataservice.complaints.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.complaints.dto.AssignOfficerRequest;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.TransitionCaseRequest;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.service.CaseWorkflowService;
import com.sih.dataservice.complaints.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@Tag(name = "Incidents", description = "Staff-facing endpoints for case management, triage, and lifecycle")
public class IncidentController {

    private final ComplaintService complaintService;
    private final CaseWorkflowService caseWorkflowService;

    public IncidentController(ComplaintService complaintService, CaseWorkflowService caseWorkflowService) {
        this.complaintService = complaintService;
        this.caseWorkflowService = caseWorkflowService;
    }

    @Operation(summary = "List incidents visible to authenticated staff based on role and jurisdiction/bank scope")
    @GetMapping
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER')")
    public ResponseEntity<ApiResponse<Page<IncidentDetailDto>>> listIncidents(
            @RequestParam(name = "status", required = false) ComplaintStatus status,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<IncidentDetailDto> incidents = complaintService.listIncidents(principal, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(incidents));
    }

    @Operation(summary = "Get full incident detail by ID (accounts, events, evidence). Returns 404 if out-of-scope.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentDetailDto>> getIncidentDetail(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        IncidentDetailDto detail = complaintService.getIncidentDetail(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @Operation(summary = "Assign or reassign an officer to an incident")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentDetailDto>> assignOfficer(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignOfficerRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        IncidentDetailDto response = caseWorkflowService.assignOfficer(
                id, request.getOfficerId(), principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Officer assigned successfully"));
    }

    @Operation(summary = "Transition case lifecycle status. Only CYBER_OFFICER can close or label cases.")
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentDetailDto>> transitionCase(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TransitionCaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        IncidentDetailDto response = caseWorkflowService.transitionCase(
                id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Case status updated successfully"));
    }
}
