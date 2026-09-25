package com.sih.dataservice.complaints.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.complaints.dto.ComplaintResponseDto;
import com.sih.dataservice.complaints.dto.CreateComplaintRequest;
import com.sih.dataservice.complaints.dto.EvidenceDto;
import com.sih.dataservice.complaints.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/complaints")
@Tag(name = "Complaints", description = "Complainant-facing endpoints for filing and tracking cases")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @Operation(summary = "File a new complaint with suspect/victim accounts")
    @PostMapping
    @PreAuthorize("hasAnyRole('COMPLAINANT', 'CYBER_OFFICER', 'POLICE')")
    public ResponseEntity<ApiResponse<ComplaintResponseDto>> createComplaint(
            @Valid @RequestBody CreateComplaintRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        ComplaintResponseDto response = complaintService.createComplaint(request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Complaint registered successfully"));
    }

    @Operation(summary = "List all complaints filed by the authenticated complainant")
    @GetMapping
    @PreAuthorize("hasRole('COMPLAINANT')")
    public ResponseEntity<ApiResponse<Page<ComplaintResponseDto>>> getMyComplaints(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ComplaintResponseDto> complaints = complaintService.getComplainantComplaints(principal, pageable);
        return ResponseEntity.ok(ApiResponse.ok(complaints));
    }

    @Operation(summary = "Get complaint status and details (complainant view with simplified status)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPLAINANT')")
    public ResponseEntity<ApiResponse<ComplaintResponseDto>> getComplaintById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        ComplaintResponseDto response = complaintService.getComplainantComplaintById(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Upload evidence file (JPEG, PNG, WEBP, PDF up to 10MB)")
    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMPLAINANT', 'CYBER_OFFICER', 'POLICE')")
    public ResponseEntity<ApiResponse<EvidenceDto>> uploadEvidence(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {

        EvidenceDto response = complaintService.uploadEvidence(id, file, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Evidence uploaded successfully"));
    }
}
