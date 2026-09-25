package com.sih.dataservice.auth.provider;

public record AuthIdentity(
        String provider,
        String subject,
        String email,
        String name,
        String phoneNumber
) {
}
