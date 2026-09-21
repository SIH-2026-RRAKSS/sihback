package com.sih.dataservice.admin.dto;

import com.sih.dataservice.users.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RosterEntryDto(
        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "Staff name is required")
        String name,

        String email,

        @NotNull(message = "Role is required")
        UserRole role,

        String bankCode,
        String jurisdictionPath,
        String initialPassword
) {
}
