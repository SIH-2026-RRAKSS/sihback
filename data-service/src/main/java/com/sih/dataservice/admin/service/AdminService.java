package com.sih.dataservice.admin.service;

import com.sih.dataservice.admin.dto.*;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.*;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final BankRepository bankRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public AdminService(
            BankRepository bankRepository,
            JurisdictionRepository jurisdictionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            Clock clock) {
        this.bankRepository = bankRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Bank createBank(CreateBankRequest request, UUID actorId, String ipAddress) {
        if (bankRepository.existsByCode(request.code())) {
            throw ApiException.badRequest("Bank with code " + request.code() + " already exists");
        }

        Bank bank = new Bank(request.code(), request.name(), request.active() != null ? request.active() : true);
        Bank saved = bankRepository.save(bank);

        auditService.log(actorId, "ADMIN", "CREATE_BANK", "BANK", saved.getId().toString(),
                "{\"code\":\"" + saved.getCode() + "\",\"name\":\"" + saved.getName() + "\"}", ipAddress);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Bank> getBanks() {
        return bankRepository.findAll();
    }

    @Transactional
    public Jurisdiction createJurisdiction(CreateJurisdictionRequest request, UUID actorId, String ipAddress) {
        if (jurisdictionRepository.existsByPath(request.path())) {
            throw ApiException.badRequest("Jurisdiction with path " + request.path() + " already exists");
        }

        Jurisdiction parent = null;
        if (request.parentId() != null) {
            parent = jurisdictionRepository.findById(request.parentId())
                    .orElseThrow(() -> ApiException.badRequest("Parent jurisdiction not found: " + request.parentId()));
        }

        Jurisdiction jurisdiction = new Jurisdiction(parent, request.level(), request.name(), request.path());
        Jurisdiction saved = jurisdictionRepository.save(jurisdiction);

        auditService.log(actorId, "ADMIN", "CREATE_JURISDICTION", "JURISDICTION", saved.getId().toString(),
                "{\"path\":\"" + saved.getPath() + "\",\"level\":\"" + saved.getLevel() + "\"}", ipAddress);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Jurisdiction> getJurisdictions() {
        return jurisdictionRepository.findAll();
    }

    @Transactional
    public User createStaffUser(CreateStaffUserRequest request, UUID actorId, String ipAddress) {
        if (userRepository.existsByEmployeeId(request.employeeId())) {
            throw ApiException.badRequest("Employee ID already exists: " + request.employeeId());
        }
        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw ApiException.badRequest("Email already exists: " + request.email());
        }

        Bank bank = null;
        if (request.role() == UserRole.BANK_EMPLOYEE || request.role() == UserRole.BANK_MANAGER) {
            if (request.bankId() == null) {
                throw ApiException.badRequest("Bank ID is required for bank staff roles");
            }
            bank = bankRepository.findById(request.bankId())
                    .orElseThrow(() -> ApiException.badRequest("Bank not found: " + request.bankId()));
        }

        Jurisdiction jurisdiction = null;
        if (request.role() == UserRole.POLICE) {
            if (request.jurisdictionId() == null) {
                throw ApiException.badRequest("Jurisdiction ID is required for police roles");
            }
            jurisdiction = jurisdictionRepository.findById(request.jurisdictionId())
                    .orElseThrow(() -> ApiException.badRequest("Jurisdiction not found: " + request.jurisdictionId()));
        }

        User user = new User();
        user.setRole(request.role());
        user.setName(request.name());
        user.setEmail(request.email());
        user.setEmployeeId(request.employeeId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBank(bank);
        user.setJurisdiction(jurisdiction);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);

        User saved = userRepository.save(user);

        auditService.log(actorId, "ADMIN", "CREATE_STAFF_USER", "USER", saved.getId().toString(),
                "{\"employeeId\":\"" + saved.getEmployeeId() + "\",\"role\":\"" + saved.getRole() + "\"}", ipAddress);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<User> getStaffUsers() {
        return userRepository.findByRoleInAndStatus(
                List.of(UserRole.BANK_EMPLOYEE, UserRole.BANK_MANAGER, UserRole.POLICE, UserRole.CYBER_OFFICER, UserRole.ADMIN),
                UserStatus.ACTIVE
        );
    }

    /**
     * Daily roster sync implementation (FR-AUTH-3, FR-ADM-1).
     * Adds new staff, updates existing staff, and immediately deactivates leavers
     * while revoking their active tokens by incrementing token_version.
     */
    @Transactional
    public RosterSyncResult syncRoster(RosterSyncRequest request, UUID actorId, String ipAddress) {
        Instant syncTime = Instant.now(clock);
        int createdCount = 0;
        int updatedCount = 0;
        int deactivatedCount = 0;

        Set<String> processedEmployeeIds = new HashSet<>();

        for (RosterEntryDto entry : request.entries()) {
            processedEmployeeIds.add(entry.employeeId());

            Bank bank = null;
            if (entry.bankCode() != null) {
                bank = bankRepository.findByCode(entry.bankCode()).orElse(null);
            }

            Jurisdiction jurisdiction = null;
            if (entry.jurisdictionPath() != null) {
                jurisdiction = jurisdictionRepository.findByPath(entry.jurisdictionPath()).orElse(null);
            }

            Optional<User> existingUserOpt = userRepository.findByEmployeeId(entry.employeeId());
            if (existingUserOpt.isPresent()) {
                User user = existingUserOpt.get();
                user.setName(entry.name());
                if (entry.email() != null) {
                    user.setEmail(entry.email());
                }
                user.setRole(entry.role());
                user.setBank(bank);
                user.setJurisdiction(jurisdiction);
                user.setStatus(UserStatus.ACTIVE);
                user.setLastRosterSync(syncTime);
                userRepository.save(user);
                updatedCount++;
            } else {
                User newUser = new User();
                newUser.setEmployeeId(entry.employeeId());
                newUser.setName(entry.name());
                newUser.setEmail(entry.email());
                newUser.setRole(entry.role());
                newUser.setBank(bank);
                newUser.setJurisdiction(jurisdiction);
                newUser.setStatus(UserStatus.ACTIVE);
                String rawPassword = entry.initialPassword() != null ? entry.initialPassword() : UUID.randomUUID().toString();
                newUser.setPasswordHash(passwordEncoder.encode(rawPassword));
                newUser.setLastRosterSync(syncTime);
                newUser.setTokenVersion(1);
                userRepository.save(newUser);
                createdCount++;
            }
        }

        // Deactivate staff not present in the new roster (leavers)
        List<User> activeStaff = userRepository.findByRoleInAndStatus(
                List.of(UserRole.BANK_EMPLOYEE, UserRole.BANK_MANAGER, UserRole.POLICE, UserRole.CYBER_OFFICER),
                UserStatus.ACTIVE
        );

        for (User staff : activeStaff) {
            if (staff.getEmployeeId() != null && !processedEmployeeIds.contains(staff.getEmployeeId())) {
                staff.setStatus(UserStatus.DEACTIVATED);
                // Instant token revocation on deactivation (FR-AUTH-3)
                staff.setTokenVersion(staff.getTokenVersion() + 1);
                userRepository.save(staff);
                deactivatedCount++;
                log.info("Roster sync deactivated employee: {}", staff.getEmployeeId());
            }
        }

        String auditDetail = String.format("{\"processed\":%d,\"created\":%d,\"updated\":%d,\"deactivated\":%d}",
                request.entries().size(), createdCount, updatedCount, deactivatedCount);

        auditService.log(actorId, "ADMIN", "ROSTER_SYNC", "ROSTER", null, auditDetail, ipAddress);

        return new RosterSyncResult(request.entries().size(), createdCount, updatedCount, deactivatedCount);
    }
}
