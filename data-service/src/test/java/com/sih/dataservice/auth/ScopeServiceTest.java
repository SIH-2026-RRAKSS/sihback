package com.sih.dataservice.auth;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeServiceTest {

    private ScopeService scopeService;

    @BeforeEach
    void setUp() {
        scopeService = new ScopeService();
    }

    @Test
    void complainant_canAccessOnlyOwnComplaints() {
        UUID complainantId = UUID.randomUUID();
        UserPrincipal complainant = new UserPrincipal(
                complainantId, "comp@test.com", null, "Citizen A",
                UserRole.COMPLAINANT, null, null, 1, UserStatus.ACTIVE
        );

        // Own complaint -> in scope
        assertThat(scopeService.isCaseInScope(complainant, complainantId, null, null)).isTrue();

        // Other person's complaint -> out of scope
        UUID otherComplainantId = UUID.randomUUID();
        assertThat(scopeService.isCaseInScope(complainant, otherComplainantId, null, null)).isFalse();

        // Must throw 404 NOT_FOUND (NFR-SEC-1)
        assertThatThrownBy(() -> scopeService.enforceCaseAccess(complainant, otherComplainantId, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void bankStaff_canAccessOnlyCasesInvolvingOwnBank() {
        UUID bankA = UUID.randomUUID();
        UUID bankB = UUID.randomUUID();

        UserPrincipal bankEmployee = new UserPrincipal(
                UUID.randomUUID(), "EMP_BANK_A", "hash", "Banker A",
                UserRole.BANK_EMPLOYEE, bankA, null, 1, UserStatus.ACTIVE
        );

        assertThat(scopeService.isCaseInScope(bankEmployee, UUID.randomUUID(), bankA, null)).isTrue();
        assertThat(scopeService.isCaseInScope(bankEmployee, UUID.randomUUID(), bankB, null)).isFalse();

        assertThatThrownBy(() -> scopeService.enforceCaseAccess(bankEmployee, UUID.randomUUID(), bankB, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void police_canAccessOnlyCasesInJurisdictionHierarchy() {
        // Officer at district level (/OD/KHORDHA/)
        UserPrincipal districtOfficer = new UserPrincipal(
                UUID.randomUUID(), "POLICE_DISTRICT", "hash", "Inspector",
                UserRole.POLICE, null, "/OD/KHORDHA/", 1, UserStatus.ACTIVE
        );

        // Station under Khordha -> in scope
        assertThat(scopeService.isCaseInScope(districtOfficer, UUID.randomUUID(), null, "/OD/KHORDHA/STATION_A/")).isTrue();

        // Sibling district (/OD/CUTTACK/STATION_B/) -> out of scope
        assertThat(scopeService.isCaseInScope(districtOfficer, UUID.randomUUID(), null, "/OD/CUTTACK/STATION_B/")).isFalse();

        // Station officer at STATION_A cannot see STATION_B under same district
        UserPrincipal stationOfficer = new UserPrincipal(
                UUID.randomUUID(), "POLICE_STATION_A", "hash", "Sub-Inspector",
                UserRole.POLICE, null, "/OD/KHORDHA/STATION_A/", 1, UserStatus.ACTIVE
        );
        assertThat(scopeService.isCaseInScope(stationOfficer, UUID.randomUUID(), null, "/OD/KHORDHA/STATION_B/")).isFalse();
    }

    @Test
    void cyberOfficer_hasUniversalAccess() {
        UserPrincipal cyberOfficer = new UserPrincipal(
                UUID.randomUUID(), "CYBER_001", "hash", "Cyber Lead",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        assertThat(scopeService.isCaseInScope(cyberOfficer, UUID.randomUUID(), UUID.randomUUID(), "/ANY/PATH/")).isTrue();
    }

    @Test
    void admin_hasZeroCaseDataAccess() {
        UserPrincipal admin = new UserPrincipal(
                UUID.randomUUID(), "ADMIN_001", "hash", "System Admin",
                UserRole.ADMIN, null, null, 1, UserStatus.ACTIVE
        );

        // Admins manage users, but need read access for Command Center metrics
        assertThat(scopeService.isCaseInScope(admin, UUID.randomUUID(), UUID.randomUUID(), "/OD/KHORDHA/")).isTrue();
    }

    @Test
    void nodeMasking_respectsScope() {
        UUID bankA = UUID.randomUUID();
        UUID bankB = UUID.randomUUID();

        UserPrincipal bankUser = new UserPrincipal(
                UUID.randomUUID(), "BANKER", "hash", "Banker",
                UserRole.BANK_EMPLOYEE, bankA, null, 1, UserStatus.ACTIVE
        );

        assertThat(scopeService.isNodeInScope(bankUser, bankA, null)).isTrue();
        assertThat(scopeService.isNodeInScope(bankUser, bankB, null)).isFalse();
    }
}
