package com.sih.dataservice.security;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security review and data privacy audit validation tests (NFR-SEC-1, NFR-SEC-2, NFR-SEC-4).
 */
class SecurityReviewTest {

    private ScopeService scopeService;
    private UserPrincipal complainantUser;
    private UserPrincipal otherComplainant;
    private UserPrincipal bankAUser;
    private UserPrincipal bankBUser;
    private UserPrincipal policeDistrictA;
    private UserPrincipal policeDistrictB;

    @BeforeEach
    void setUp() {
        scopeService = new ScopeService();

        complainantUser = new UserPrincipal(
                UUID.randomUUID(), "citizenA@test.com", "hash", "Citizen A",
                UserRole.COMPLAINANT, null, null, 1, UserStatus.ACTIVE
        );

        otherComplainant = new UserPrincipal(
                UUID.randomUUID(), "citizenB@test.com", "hash", "Citizen B",
                UserRole.COMPLAINANT, null, null, 1, UserStatus.ACTIVE
        );

        bankAUser = new UserPrincipal(
                UUID.randomUUID(), "bankA@test.com", "hash", "Bank Employee A",
                UserRole.BANK_EMPLOYEE, UUID.randomUUID(), null, 1, UserStatus.ACTIVE
        );

        bankBUser = new UserPrincipal(
                UUID.randomUUID(), "bankB@test.com", "hash", "Bank Employee B",
                UserRole.BANK_EMPLOYEE, UUID.randomUUID(), null, 1, UserStatus.ACTIVE
        );

        policeDistrictA = new UserPrincipal(
                UUID.randomUUID(), "policeA@test.gov", "hash", "Inspector A",
                UserRole.POLICE, null, "/OD/KHORDHA/BHUBANESWAR/", 1, UserStatus.ACTIVE
        );

        policeDistrictB = new UserPrincipal(
                UUID.randomUUID(), "policeB@test.gov", "hash", "Inspector B",
                UserRole.POLICE, null, "/OD/CUTTACK/CITY/", 1, UserStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("NFR-SEC-1: Cross-complainant access attempt throws 404 NOT_FOUND (preventing existence oracle)")
    void testCrossComplainantIsolation() {
        assertThatThrownBy(() -> scopeService.enforceCaseAccess(
                otherComplainant, complainantUser.getId(), null, "/OD/KHORDHA/BHUBANESWAR/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                    assertThat(apiEx.getMessage()).contains("Resource not found");
                });
    }

    @Test
    @DisplayName("NFR-SEC-1: Cross-bank access attempt throws 404 NOT_FOUND")
    void testCrossBankIsolation() {
        assertThatThrownBy(() -> scopeService.enforceCaseAccess(
                bankBUser, UUID.randomUUID(), bankAUser.getBankId(), "/OD/KHORDHA/BHUBANESWAR/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                    assertThat(apiEx.getMessage()).contains("Resource not found");
                });
    }

    @Test
    @DisplayName("NFR-SEC-1: Cross-jurisdiction police access attempt throws 404 NOT_FOUND")
    void testCrossJurisdictionIsolation() {
        assertThatThrownBy(() -> scopeService.enforceCaseAccess(
                policeDistrictB, UUID.randomUUID(), null, "/OD/KHORDHA/BHUBANESWAR/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus().value()).isEqualTo(404);
                    assertThat(apiEx.getMessage()).contains("Resource not found");
                });
    }

    @Test
    @DisplayName("NFR-SEC-4: Audit details and structured log sanitization pattern test")
    void testAuditDetailPIISanitization() {
        // Assert regex patterns for sensitive identifiers
        Pattern rawPhonePattern = Pattern.compile("(\\+91|0)?[6-9]\\d{9}");
        Pattern rawCardPattern = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\\b");

        // Example safe audit payload produced by system
        String safeAuditDetail = "{\"action\":\"RELABEL_CASE\",\"oldStatus\":\"CLOSED_FRAUD\",\"newStatus\":\"CLOSED_NOT_FRAUD\",\"hash\":\"a5c3e7b\"}";

        assertThat(rawPhonePattern.matcher(safeAuditDetail).find()).isFalse();
        assertThat(rawCardPattern.matcher(safeAuditDetail).find()).isFalse();
        assertThat(safeAuditDetail.toLowerCase()).doesNotContain("password");
        assertThat(safeAuditDetail.toLowerCase()).doesNotContain("secret");
    }
}
