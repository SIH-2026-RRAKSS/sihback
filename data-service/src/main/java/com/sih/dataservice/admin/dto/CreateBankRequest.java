package com.sih.dataservice.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBankRequest(
        @NotBlank(message = "Bank code is required")
        String code,

        @NotBlank(message = "Bank name is required")
        String name,

        Boolean active
) {
}
