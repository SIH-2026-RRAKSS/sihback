package com.sih.dataservice.auth.controller;

import com.sih.dataservice.auth.dto.AuthTokenResponse;
import com.sih.dataservice.auth.dto.ComplainantLoginRequest;
import com.sih.dataservice.auth.dto.RefreshTokenRequest;
import com.sih.dataservice.auth.dto.StaffLoginRequest;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.service.AuthService;
import com.sih.dataservice.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication and Token endpoints for complainants and staff")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Complainant login via Identity Provider (Mock / Google)")
    @PostMapping("/complainant/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginComplainant(
            @Valid @RequestBody ComplainantLoginRequest request,
            HttpServletRequest httpRequest) {
        
        AuthTokenResponse response = authService.loginComplainant(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @Operation(summary = "Staff login using employee ID and password")
    @PostMapping("/staff/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginStaff(
            @Valid @RequestBody StaffLoginRequest request,
            HttpServletRequest httpRequest) {

        AuthTokenResponse response = authService.loginStaff(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Staff authentication successful"));
    }

    @Operation(summary = "Refresh access token using valid refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed successfully"));
    }

    @Operation(summary = "Logout and invalidate active tokens")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        if (principal != null) {
            authService.logout(principal.getId(), httpRequest.getRemoteAddr());
        }
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }
}
