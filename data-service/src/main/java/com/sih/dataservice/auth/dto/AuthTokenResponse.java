package com.sih.dataservice.auth.dto;

import com.sih.dataservice.users.entity.UserRole;

import java.util.UUID;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String name,
        UserRole role,
        UUID bankId,
        String jurisdictionPath
) {
}
