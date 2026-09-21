package com.sih.dataservice.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ComplainantLoginRequest(
        @NotBlank(message = "Provider is required (e.g. MOCK, GOOGLE)")
        String provider,

        @NotBlank(message = "Credential token is required")
        String credentialToken
) {
}
