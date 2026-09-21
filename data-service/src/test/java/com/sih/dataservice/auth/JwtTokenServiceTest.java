package com.sih.dataservice.auth;

import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, 900000, 604800000);
    }

    @Test
    void generateAccessToken_containsAllScopeAndPrincipalClaims() {
        UUID userId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        String jurisdictionPath = "/OD/KHORDHA/";

        UserPrincipal principal = new UserPrincipal(
                userId, "EMP123", "hash", "Officer Singh",
                UserRole.POLICE, bankId, jurisdictionPath, 2, UserStatus.ACTIVE
        );

        String token = jwtTokenService.generateAccessToken(principal);
        assertThat(token).isNotBlank();

        Claims claims = jwtTokenService.parseAndValidateToken(token);
        assertThat(claims).isNotNull();
        assertThat(jwtTokenService.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtTokenService.extractRole(claims)).isEqualTo(UserRole.POLICE);
        assertThat(jwtTokenService.extractBankId(claims)).isEqualTo(bankId);
        assertThat(jwtTokenService.extractJurisdictionPath(claims)).isEqualTo(jurisdictionPath);
        assertThat(jwtTokenService.extractTokenVersion(claims)).isEqualTo(2);
        assertThat(jwtTokenService.extractTokenType(claims)).isEqualTo(JwtTokenService.TYPE_ACCESS);
    }

    @Test
    void generateRefreshToken_containsVersionAndRefreshType() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId, "complainant@example.com", null, "Citizen",
                UserRole.COMPLAINANT, null, null, 1, UserStatus.ACTIVE
        );

        String refreshToken = jwtTokenService.generateRefreshToken(principal);
        assertThat(refreshToken).isNotBlank();

        Claims claims = jwtTokenService.parseAndValidateToken(refreshToken);
        assertThat(claims).isNotNull();
        assertThat(jwtTokenService.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtTokenService.extractTokenVersion(claims)).isEqualTo(1);
        assertThat(jwtTokenService.extractTokenType(claims)).isEqualTo(JwtTokenService.TYPE_REFRESH);
    }

    @Test
    void parseAndValidateToken_returnsNullOnTamperedToken() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId, "EMP001", "hash", "Admin",
                UserRole.ADMIN, null, null, 1, UserStatus.ACTIVE
        );

        String token = jwtTokenService.generateAccessToken(principal);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        Claims claims = jwtTokenService.parseAndValidateToken(tamperedToken);
        assertThat(claims).isNull();
    }
}
