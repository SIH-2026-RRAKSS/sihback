package com.sih.dataservice.admin.controller;

import com.sih.dataservice.admin.dto.*;
import com.sih.dataservice.admin.service.AdminService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.Jurisdiction;
import com.sih.dataservice.users.entity.User;
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

@RestController
@RequestMapping("/admin")
@Tag(name = "Administration", description = "Admin endpoints for banks, jurisdictions, staff, and roster sync")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Create a new bank")
    @PostMapping("/banks")
    public ResponseEntity<ApiResponse<Bank>> createBank(
            @Valid @RequestBody CreateBankRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UUID actorId = principal != null ? principal.getId() : null;
        Bank bank = adminService.createBank(request, actorId, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(bank, "Bank created successfully"));
    }

    @Operation(summary = "List all banks")
    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<Bank>>> getBanks() {
        List<Bank> banks = adminService.getBanks();
        return ResponseEntity.ok(ApiResponse.ok(banks));
    }

    @Operation(summary = "Create a new jurisdiction")
    @PostMapping("/jurisdictions")
    public ResponseEntity<ApiResponse<Jurisdiction>> createJurisdiction(
            @Valid @RequestBody CreateJurisdictionRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UUID actorId = principal != null ? principal.getId() : null;
        Jurisdiction jurisdiction = adminService.createJurisdiction(request, actorId, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(jurisdiction, "Jurisdiction created successfully"));
    }

    @Operation(summary = "List all jurisdictions")
    @GetMapping("/jurisdictions")
    public ResponseEntity<ApiResponse<List<Jurisdiction>>> getJurisdictions() {
        List<Jurisdiction> jurisdictions = adminService.getJurisdictions();
        return ResponseEntity.ok(ApiResponse.ok(jurisdictions));
    }

    @Operation(summary = "Create a new staff user (bank employee, police, cyber officer, admin)")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<User>> createStaffUser(
            @Valid @RequestBody CreateStaffUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UUID actorId = principal != null ? principal.getId() : null;
        User user = adminService.createStaffUser(request, actorId, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(user, "Staff account created successfully"));
    }

    @Operation(summary = "List all active staff users")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getStaffUsers() {
        List<User> users = adminService.getStaffUsers();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @Operation(summary = "Run daily staff roster sync with automatic deactivation and token revocation")
    @PostMapping("/roster-sync")
    public ResponseEntity<ApiResponse<RosterSyncResult>> rosterSync(
            @Valid @RequestBody RosterSyncRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UUID actorId = principal != null ? principal.getId() : null;
        RosterSyncResult result = adminService.syncRoster(request, actorId, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(result, "Roster sync completed successfully"));
    }
}
