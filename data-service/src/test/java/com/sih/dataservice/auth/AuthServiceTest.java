package com.sih.dataservice.auth;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.dto.AuthTokenResponse;
import com.sih.dataservice.auth.dto.ComplainantLoginRequest;
import com.sih.dataservice.auth.dto.RefreshTokenRequest;
import com.sih.dataservice.auth.dto.StaffLoginRequest;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.provider.AuthIdentity;
import com.sih.dataservice.auth.provider.IdentityProvider;
import com.sih.dataservice.auth.service.AuthService;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdentityProvider identityProvider;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(identityProvider.getProviderName()).thenReturn("MOCK");
        authService = new AuthService(
                userRepository,
                List.of(identityProvider),
                jwtTokenService,
                passwordEncoder,
                cryptoService,
                auditService
        );
    }

    @Test
    void loginComplainant_createsNewUserOnFirstLogin() {
        ComplainantLoginRequest request = new ComplainantLoginRequest("MOCK", "mock-sub-101");
        AuthIdentity identity = new AuthIdentity("MOCK", "mock-sub-101", "user@mock.com", "Ramesh Kumar", "+919876543210");

        when(identityProvider.verifyToken("mock-sub-101")).thenReturn(identity);
        when(userRepository.findByOauthProviderAndOauthSubject("MOCK", "mock-sub-101")).thenReturn(Optional.empty());
        when(cryptoService.encrypt("+919876543210")).thenReturn("encrypted-phone");
        when(cryptoService.computeHmac("+919876543210")).thenReturn("hashed-phone");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setRole(UserRole.COMPLAINANT);
        savedUser.setName(identity.name());
        savedUser.setStatus(UserStatus.ACTIVE);
        savedUser.setTokenVersion(1);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenService.generateAccessToken(any())).thenReturn("mock-access-token");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("mock-refresh-token");

        AuthTokenResponse response = authService.loginComplainant(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        assertThat(response.role()).isEqualTo(UserRole.COMPLAINANT);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPhoneEncrypted()).isEqualTo("encrypted-phone");
        assertThat(userCaptor.getValue().getPhoneHash()).isEqualTo("hashed-phone");
    }

    @Test
    void loginStaff_verifiesBcryptPasswordAndReturnsTokens() {
        StaffLoginRequest request = new StaffLoginRequest("EMP999", "correct-password");

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmployeeId("EMP999");
        staff.setPasswordHash("$2a$12$hashedpassword");
        staff.setRole(UserRole.CYBER_OFFICER);
        staff.setStatus(UserStatus.ACTIVE);
        staff.setTokenVersion(1);

        when(userRepository.findByEmployeeId("EMP999")).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("correct-password", "$2a$12$hashedpassword")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token-staff");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-token-staff");

        AuthTokenResponse response = authService.loginStaff(request, "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token-staff");
        assertThat(response.role()).isEqualTo(UserRole.CYBER_OFFICER);
        verify(auditService).log(eq(staff.getId()), eq("CYBER_OFFICER"), eq("LOGIN_SUCCESS"), eq("USER"), any(), any(), eq("127.0.0.1"));
    }

    @Test
    void loginStaff_failsOnBadPassword() {
        StaffLoginRequest request = new StaffLoginRequest("EMP999", "wrong-password");

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmployeeId("EMP999");
        staff.setPasswordHash("$2a$12$hashedpassword");
        staff.setRole(UserRole.POLICE);
        staff.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmployeeId("EMP999")).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("wrong-password", "$2a$12$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginStaff(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void logout_incrementsTokenVersionForInstantRevocation() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRole(UserRole.POLICE);
        user.setTokenVersion(3);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.logout(userId, "127.0.0.1");

        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRepository).save(user);
    }
}
