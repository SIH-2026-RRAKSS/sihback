package com.sih.dataservice.admin.dto;

import com.sih.dataservice.users.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateStaffUserRequest(
        @NotNull(message = "Role is required")
        UserRole role,

        @NotBlank(message = "Name is required")
        String name,

        @Email(message = "Valid email is required")
        String email,

        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "Password is required")
        String password,

        UUID bankId,
        UUID jurisdictionId
) {
}
