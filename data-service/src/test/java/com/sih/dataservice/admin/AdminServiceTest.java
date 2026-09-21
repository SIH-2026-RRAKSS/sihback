package com.sih.dataservice.admin;

import com.sih.dataservice.admin.dto.*;
import com.sih.dataservice.admin.service.AdminService;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.users.entity.*;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private BankRepository bankRepository;

    @Mock
    private JurisdictionRepository jurisdictionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private Clock fixedClock;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneId.of("UTC"));
        adminService = new AdminService(
                bankRepository,
                jurisdictionRepository,
                userRepository,
                passwordEncoder,
                auditService,
                fixedClock
        );
    }

    @Test
    void createBank_savesAndAudits() {
        CreateBankRequest request = new CreateBankRequest("SBI", "State Bank of India", true);
        when(bankRepository.existsByCode("SBI")).thenReturn(false);

        Bank bank = new Bank("SBI", "State Bank of India", true);
        bank.setId(UUID.randomUUID());
        when(bankRepository.save(any(Bank.class))).thenReturn(bank);

        UUID adminId = UUID.randomUUID();
        Bank created = adminService.createBank(request, adminId, "127.0.0.1");

        assertThat(created).isNotNull();
        assertThat(created.getCode()).isEqualTo("SBI");
        verify(auditService).log(eq(adminId), eq("ADMIN"), eq("CREATE_BANK"), eq("BANK"), any(), any(), eq("127.0.0.1"));
    }

    @Test
    void syncRoster_createsUpdatesAndDeactivatesLeaversWithRevocation() {
        // Prepare roster with 2 staff: 1 new ("EMP_NEW"), 1 existing ("EMP_EXISTING")
        // In the DB, there is "EMP_EXISTING" and "EMP_LEAVER"
        RosterEntryDto newStaff = new RosterEntryDto("EMP_NEW", "Officer New", "new@police.gov.in", UserRole.POLICE, null, "/OD/KHORDHA/", "pass");
        RosterEntryDto existingStaff = new RosterEntryDto("EMP_EXISTING", "Officer Updated", "existing@police.gov.in", UserRole.POLICE, null, "/OD/KHORDHA/", null);

        RosterSyncRequest request = new RosterSyncRequest(List.of(newStaff, existingStaff));

        User existingUserInDb = new User();
        existingUserInDb.setId(UUID.randomUUID());
        existingUserInDb.setEmployeeId("EMP_EXISTING");
        existingUserInDb.setName("Officer Old Name");
        existingUserInDb.setStatus(UserStatus.ACTIVE);
        existingUserInDb.setTokenVersion(1);

        User leaverUserInDb = new User();
        leaverUserInDb.setId(UUID.randomUUID());
        leaverUserInDb.setEmployeeId("EMP_LEAVER");
        leaverUserInDb.setName("Officer Leaving");
        leaverUserInDb.setRole(UserRole.POLICE);
        leaverUserInDb.setStatus(UserStatus.ACTIVE);
        leaverUserInDb.setTokenVersion(2);

        when(userRepository.findByEmployeeId("EMP_NEW")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeId("EMP_EXISTING")).thenReturn(Optional.of(existingUserInDb));
        when(passwordEncoder.encode(any())).thenReturn("encoded-pass");

        // When finding active staff to check for leavers
        when(userRepository.findByRoleInAndStatus(any(), eq(UserStatus.ACTIVE)))
                .thenReturn(List.of(existingUserInDb, leaverUserInDb));

        UUID adminId = UUID.randomUUID();
        RosterSyncResult result = adminService.syncRoster(request, adminId, "127.0.0.1");

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.deactivatedCount()).isEqualTo(1);

        // Verify leaver was deactivated and token_version incremented for instant token revocation (FR-AUTH-3)
        assertThat(leaverUserInDb.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(leaverUserInDb.getTokenVersion()).isEqualTo(3);

        verify(auditService).log(eq(adminId), eq("ADMIN"), eq("ROSTER_SYNC"), eq("ROSTER"), any(), any(), eq("127.0.0.1"));
    }
}
