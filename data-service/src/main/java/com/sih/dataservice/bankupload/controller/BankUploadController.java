package com.sih.dataservice.bankupload.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.bankupload.dto.BankUploadResponseDto;
import com.sih.dataservice.bankupload.dto.ReviewUploadRequest;
import com.sih.dataservice.bankupload.service.BankUploadService;
import com.sih.dataservice.common.dto.ApiResponse;
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
@RequestMapping("/bank-uploads")
@Tag(name = "Bank Uploads", description = "Endpoints for bank transaction batch uploads and two-person approvals")
public class BankUploadController {

    private final BankUploadService bankUploadService;

    public BankUploadController(BankUploadService bankUploadService) {
        this.bankUploadService = bankUploadService;
    }

    @Operation(summary = "Upload transaction batch CSV file as PENDING (Bank Employee)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE', 'BANK_MANAGER')")
    public ResponseEntity<ApiResponse<BankUploadResponseDto>> uploadTransactions(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        BankUploadResponseDto response = bankUploadService.uploadTransactions(
                file, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Transaction batch uploaded and pending manager review"));
    }

    @Operation(summary = "List transaction uploads for caller bank")
    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE', 'BANK_MANAGER', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<Page<BankUploadResponseDto>>> listUploads(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<BankUploadResponseDto> uploads = bankUploadService.listUploads(principal, pageable);
        return ResponseEntity.ok(ApiResponse.ok(uploads));
    }

    @Operation(summary = "Review and approve/reject upload with two-person rule (Bank Manager, reviewer != uploader)")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('BANK_MANAGER', 'CYBER_OFFICER')")
    public ResponseEntity<ApiResponse<BankUploadResponseDto>> reviewUpload(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReviewUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        BankUploadResponseDto response = bankUploadService.reviewUpload(
                id, request, principal, httpRequest.getRemoteAddr());
        String action = request.getStatus().name().toLowerCase();
        return ResponseEntity.ok(ApiResponse.ok(response, "Upload " + action + " successfully"));
    }
}
