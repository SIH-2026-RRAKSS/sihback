package com.sih.dataservice.auth.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.dto.AuthTokenResponse;
import com.sih.dataservice.auth.dto.ComplainantLoginRequest;
import com.sih.dataservice.auth.dto.RefreshTokenRequest;
import com.sih.dataservice.auth.dto.StaffLoginRequest;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.provider.AuthIdentity;
import com.sih.dataservice.auth.provider.IdentityProvider;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final List<IdentityProvider> identityProviders;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final CryptoService cryptoService;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            List<IdentityProvider> identityProviders,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            CryptoService cryptoService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.identityProviders = identityProviders;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthTokenResponse loginComplainant(ComplainantLoginRequest request, String ipAddress) {
        IdentityProvider provider = identityProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(request.provider()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unsupported identity provider: " + request.provider()));

        AuthIdentity identity = provider.verifyToken(request.credentialToken());

        User user = userRepository.findByOauthProviderAndOauthSubject(identity.provider(), identity.subject())
                .orElseGet(() -> {
                    // First login creates complainant (FR-AUTH-1)
                    User newUser = new User();
                    newUser.setRole(UserRole.COMPLAINANT);
                    newUser.setName(identity.name() != null ? identity.name() : "Complainant");
                    newUser.setEmail(identity.email());
                    newUser.setOauthProvider(identity.provider());
                    newUser.setOauthSubject(identity.subject());
                    newUser.setStatus(UserStatus.ACTIVE);

                    if (identity.phoneNumber() != null) {
                        newUser.setPhoneEncrypted(cryptoService.encrypt(identity.phoneNumber()));
                        newUser.setPhoneHash(cryptoService.computeHmac(identity.phoneNumber()));
                    }

                    return userRepository.save(newUser);
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Account has been deactivated");
        }

        UserPrincipal principal = UserPrincipal.fromUser(user);
        String accessToken = jwtTokenService.generateAccessToken(principal);
        String refreshToken = jwtTokenService.generateRefreshToken(principal);

        auditService.log(user.getId(), user.getRole().name(), "LOGIN_SUCCESS", "USER", user.getId().toString(),
                "{\"provider\":\"" + identity.provider() + "\"}", ipAddress);

        return buildResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthTokenResponse loginStaff(StaffLoginRequest request, String ipAddress) {
        User user = userRepository.findByEmployeeId(request.employeeId())
                .orElseThrow(() -> {
                    auditService.log(null, null, "LOGIN_FAILED", "USER", null,
                            "{\"employeeId\":\"" + request.employeeId() + "\",\"reason\":\"NOT_FOUND\"}", ipAddress);
                    return ApiException.unauthorized("Invalid employee ID or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user.getId(), user.getRole().name(), "LOGIN_FAILED", "USER", user.getId().toString(),
                    "{\"reason\":\"BAD_CREDENTIALS\"}", ipAddress);
            throw ApiException.unauthorized("Invalid employee ID or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.log(user.getId(), user.getRole().name(), "LOGIN_BLOCKED", "USER", user.getId().toString(),
                    "{\"reason\":\"DEACTIVATED\"}", ipAddress);
            throw ApiException.unauthorized("Account has been deactivated");
        }

        UserPrincipal principal = UserPrincipal.fromUser(user);
        String accessToken = jwtTokenService.generateAccessToken(principal);
        String refreshToken = jwtTokenService.generateRefreshToken(principal);

        auditService.log(user.getId(), user.getRole().name(), "LOGIN_SUCCESS", "USER", user.getId().toString(),
                "{\"method\":\"PASSWORD\"}", ipAddress);

        return buildResponse(user, accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims = jwtTokenService.parseAndValidateToken(request.refreshToken());
        if (claims == null || !JwtTokenService.TYPE_REFRESH.equals(jwtTokenService.extractTokenType(claims))) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        UUID userId = jwtTokenService.extractUserId(claims);
        int tokenVersion = jwtTokenService.extractTokenVersion(claims);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Account has been deactivated");
        }

        if (user.getTokenVersion() != tokenVersion) {
            throw ApiException.unauthorized("Refresh token has been revoked");
        }

        UserPrincipal principal = UserPrincipal.fromUser(user);
        String newAccessToken = jwtTokenService.generateAccessToken(principal);
        String newRefreshToken = jwtTokenService.generateRefreshToken(principal);

        return buildResponse(user, newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(UUID userId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        // Instant token revocation by bumping token_version (FR-AUTH-3, FR-AUTH-4)
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditService.log(user.getId(), user.getRole().name(), "LOGOUT", "USER", user.getId().toString(),
                "{\"revokedVersion\":" + (user.getTokenVersion() - 1) + "}", ipAddress);
    }

    private AuthTokenResponse buildResponse(User user, String accessToken, String refreshToken) {
        UUID bankId = user.getBank() != null ? user.getBank().getId() : null;
        String jurisdictionPath = user.getJurisdiction() != null ? user.getJurisdiction().getPath() : null;

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                900, // 15 minutes
                user.getId(),
                user.getName(),
                user.getRole(),
                bankId,
                jurisdictionPath
        );
    }
}
