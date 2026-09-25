package com.sih.dataservice.auth.provider;

import com.sih.dataservice.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component("mockIdentityProvider")
@Profile({"dev", "test"})
public class MockIdentityProvider implements IdentityProvider {

    private final Environment environment;

    public MockIdentityProvider(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProfile() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.contains("dev") && !activeProfiles.contains("test")) {
            throw new IllegalStateException("MockIdentityProvider is strictly forbidden outside dev and test profiles (FR-AUTH-6)");
        }
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public AuthIdentity verifyToken(String credentialToken) {
        if (credentialToken == null || credentialToken.trim().isEmpty()) {
            throw ApiException.badRequest("Mock token cannot be blank");
        }

        // Format 1: mock:<subject>:<name>:<email>:<phone>
        if (credentialToken.startsWith("mock:")) {
            String[] parts = credentialToken.split(":", 5);
            String subject = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : "mock-sub-default";
            String name = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : "Mock Complainant";
            String email = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : subject + "@mock.com";
            String phone = parts.length > 4 && !parts[4].isEmpty() ? parts[4] : "+919876543210";
            return new AuthIdentity("MOCK", subject, email, name, phone);
        }

        // Format 2: simple subject string
        String subject = credentialToken.trim();
        return new AuthIdentity(
                "MOCK",
                subject,
                subject + "@mock.example.com",
                "Mock Complainant " + subject,
                "+919876543210"
        );
    }
}
