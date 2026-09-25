package com.sih.dataservice.complaints.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.complaints.dto.AssignOfficerRequest;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.TransitionCaseRequest;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.service.CaseWorkflowService;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.dossier.dto.DossierFormat;
import com.sih.dataservice.dossier.dto.IncidentDossierDto;
import com.sih.dataservice.dossier.service.DossierService;
import com.sih.dataservice.graph.dto.SubgraphResponseDto;
import com.sih.dataservice.graph.service.GraphService;
import com.sih.dataservice.ml.dto.PredictionDto;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
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
    private final GraphService graphService;
    private final PredictionService predictionService;
    private final DossierService dossierService;
    private final com.sih.dataservice.freeze.service.FreezeRequestService freezeRequestService;

    public IncidentController(ComplaintService complaintService,
                              CaseWorkflowService caseWorkflowService,
                              GraphService graphService,
                              PredictionService predictionService,
                              DossierService dossierService,
                              com.sih.dataservice.freeze.service.FreezeRequestService freezeRequestService) {
        this.complaintService = complaintService;
        this.caseWorkflowService = caseWorkflowService;
        this.graphService = graphService;
        this.predictionService = predictionService;
        this.dossierService = dossierService;
        this.freezeRequestService = freezeRequestService;
    }

    @Operation(summary = "List incidents visible to authenticated staff based on role and jurisdiction/bank scope")
    @GetMapping("/legacy")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<IncidentDetailDto>>> listIncidents(
            @RequestParam(name = "status", required = false) ComplaintStatus status,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<IncidentDetailDto> incidents = complaintService.listIncidents(principal, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(incidents));
    }

    @Operation(summary = "Get full incident detail by ID (accounts, events, evidence). Returns 404 if out-of-scope.")
    @GetMapping("/legacy/{id}")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
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

    @Operation(summary = "Get K-hop incident graph around complaint accounts with scope masking")
    @GetMapping("/legacy/{id}/graph")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SubgraphResponseDto>> getIncidentGraph(
            @PathVariable("id") UUID id,
            @RequestParam(name = "hops", defaultValue = "2") int hops,
            @RequestParam(name = "maxNodes", defaultValue = "50") int maxNodes,
            @AuthenticationPrincipal UserPrincipal principal) {

        SubgraphResponseDto graph = graphService.getIncidentGraph(id, hops, maxNodes, principal);
        return ResponseEntity.ok(ApiResponse.ok(graph));
    }

    @Operation(summary = "Score complaint with ML model service and auto-triage (falls back to anomaly gate)")
    @PostMapping("/{id}/predict")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<PredictionDto>> predict(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Validate scope
        complaintService.getIncidentDetail(id, principal);
        Prediction prediction = predictionService.scoreComplaint(id);
        return ResponseEntity.ok(ApiResponse.ok(PredictionDto.fromEntity(prediction)));
    }

    @Operation(summary = "Export complete incident dossier in JSON, Markdown, or HTML format")
    @GetMapping(value = "/{id}/dossier")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    public ResponseEntity<?> getDossier(
            @PathVariable("id") UUID id,
            @RequestParam(name = "format", defaultValue = "JSON") DossierFormat format,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        String clientIp = httpRequest.getRemoteAddr();
        switch (format) {
            case MARKDOWN:
                String md = dossierService.exportMarkdown(id, principal, clientIp);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=UTF-8")
                        .body(md);
            case HTML:
                String html = dossierService.exportHtml(id, principal, clientIp);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                        .body(html);
            case JSON:
            default:
                IncidentDossierDto dto = dossierService.getDossier(id, principal, clientIp);
                return ResponseEntity.ok(ApiResponse.ok(dto));
        }
    }

    @Operation(summary = "Get all freeze requests associated with this incident")
    @GetMapping("/{id}/freeze-requests")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<com.sih.dataservice.freeze.dto.FreezeRequestResponseDto>>> getIncidentFreezeRequests(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        java.util.List<com.sih.dataservice.freeze.dto.FreezeRequestResponseDto> list = freezeRequestService.getFreezeRequestsForComplaint(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(summary = "Update fraud label of a closed case post-closure (FR-LBL-1). Restricted strictly to CYBER_OFFICER.")
    @PutMapping("/{id}/label")
    @PreAuthorize("hasRole('CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentDetailDto>> updateCaseLabel(
            @PathVariable("id") UUID id,
            @Valid @RequestBody com.sih.dataservice.complaints.dto.UpdateCaseLabelRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        IncidentDetailDto response = caseWorkflowService.updateCaseLabel(
                id, request.getLabel(), request.getReason(), principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Case label updated successfully"));
    }
}




