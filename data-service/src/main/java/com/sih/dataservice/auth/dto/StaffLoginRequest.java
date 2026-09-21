package com.sih.dataservice.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffLoginRequest(
        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "Password is required")
        String password
) {
}
