package com.sih.dataservice.freeze.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.freeze.dto.CreateFreezeRequestDto;
import com.sih.dataservice.freeze.dto.FreezeRequestResponseDto;
import com.sih.dataservice.freeze.dto.RespondFreezeRequestDto;
import com.sih.dataservice.freeze.scheduler.FreezeSlaScheduler;
import com.sih.dataservice.freeze.service.FreezeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/freeze-requests")
@Tag(name = "Freeze Requests", description = "Account freeze and information request management with 7-day SLA")
@SecurityRequirement(name = "BearerAuth")
public class FreezeRequestController {

    private final FreezeRequestService freezeRequestService;
    private final FreezeSlaScheduler freezeSlaScheduler;

    public FreezeRequestController(FreezeRequestService freezeRequestService, FreezeSlaScheduler freezeSlaScheduler) {
        this.freezeRequestService = freezeRequestService;
        this.freezeSlaScheduler = freezeSlaScheduler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER')")
    @Operation(summary = "Raise a freeze/info request against an account (FR-FRZ-1)")
    public ResponseEntity<ApiResponse<FreezeRequestResponseDto>> createFreezeRequest(
            @Valid @RequestBody CreateFreezeRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        FreezeRequestResponseDto response = freezeRequestService.createFreezeRequest(dto, principal, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER')")
    @Operation(summary = "List freeze requests scoped to caller role")
    public ResponseEntity<ApiResponse<Page<FreezeRequestResponseDto>>> listFreezeRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FreezeRequestResponseDto> response = freezeRequestService.listFreezeRequests(principal, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER')")
    @Operation(summary = "Get single freeze request by ID with scope validation")
    public ResponseEntity<ApiResponse<FreezeRequestResponseDto>> getFreezeRequest(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        FreezeRequestResponseDto response = freezeRequestService.getFreezeRequest(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE', 'BANK_MANAGER')")
    @Operation(summary = "Bank responds to freeze/info request (FR-FRZ-2)")
    public ResponseEntity<ApiResponse<FreezeRequestResponseDto>> respondFreezeRequest(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RespondFreezeRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        FreezeRequestResponseDto response = freezeRequestService.respondFreezeRequest(id, dto, principal, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/run-sla-check")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    @Operation(summary = "Manually trigger 7-day SLA check for day-5 reminder and overdue escalation")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runSlaCheck() {
        Map<String, Integer> result = freezeSlaScheduler.runSlaCheck();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
