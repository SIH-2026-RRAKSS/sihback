package com.sih.dataservice.auth.provider;

import com.sih.dataservice.common.exception.ApiException;
import org.springframework.stereotype.Component;

@Component("googleIdentityProvider")
public class GoogleIdentityProvider implements IdentityProvider {

    @Override
    public String getProviderName() {
        return "GOOGLE";
    }

    @Override
    public AuthIdentity verifyToken(String credentialToken) {
        if (credentialToken == null || credentialToken.trim().isEmpty()) {
            throw ApiException.badRequest("Google credential token cannot be blank");
        }

        // For dev/test environments and mockable verifications:
        // Format: google:<subject>:<email>:<name>
        if (credentialToken.startsWith("google:")) {
            String[] parts = credentialToken.split(":", 4);
            String subject = parts.length > 1 ? parts[1] : "google-default-sub";
            String email = parts.length > 2 ? parts[2] : subject + "@gmail.com";
            String name = parts.length > 3 ? parts[3] : "Google User " + subject;
            return new AuthIdentity("GOOGLE", subject, email, name, null);
        }

        // Simulating Google token verification
        return new AuthIdentity(
                "GOOGLE",
                credentialToken.hashCode() + "",
                "user" + Math.abs(credentialToken.hashCode()) + "@gmail.com",
                "Google User",
                null
        );
    }
}
