package com.sih.dataservice.bankupload.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.bankupload.dto.BankUploadResponseDto;
import com.sih.dataservice.bankupload.dto.ReviewUploadRequest;
import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.bankupload.entity.BankUploadStatus;
import com.sih.dataservice.bankupload.repository.BankUploadRepository;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.repository.FinancialEntityRepository;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankUploadServiceTest {

    @Mock
    private BankUploadRepository bankUploadRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private FinancialEntityRepository financialEntityRepository;
    @Mock
    private BankRepository bankRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private TemporalGraphEngine graphEngine;
    @Mock
    private AuditService auditService;

    @TempDir
    Path tempDir;

    private Clock fixedClock;
    private BankUploadService service;

    private Bank testBank;
    private User bankEmployee;
    private User bankManager;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
        service = new BankUploadService(
                bankUploadRepository,
                transactionRepository,
                financialEntityRepository,
                bankRepository,
                userRepository,
                cryptoService,
                graphEngine,
                auditService,
                fixedClock,
                tempDir.toString()
        );

        testBank = new Bank();
        testBank.setId(UUID.randomUUID());
        testBank.setCode("HDFC");
        testBank.setName("HDFC Bank");

        bankEmployee = new User();
        bankEmployee.setId(UUID.randomUUID());
        bankEmployee.setName("Ramesh Clerk");
        bankEmployee.setRole(UserRole.BANK_EMPLOYEE);
        bankEmployee.setBank(testBank);

        bankManager = new User();
        bankManager.setId(UUID.randomUUID());
        bankManager.setName("Suresh Manager");
        bankManager.setRole(UserRole.BANK_MANAGER);
        bankManager.setBank(testBank);
    }

    @Test
    void uploadTransactionsSuccessfullySavesAsPending() {
        String csvContent = "utr,sender,receiver,amount,timestamp\n" +
                "UTR001,ACC123,ACC456,1500.00,2026-09-22T00:00:00Z\n" +
                "UTR002,ACC789,ACC456,2500.00,2026-09-22T00:05:00Z\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "tx.csv", "text/csv", csvContent.getBytes());

        UserPrincipal employeePrincipal = UserPrincipal.fromUser(bankEmployee);

        when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
        when(userRepository.findById(bankEmployee.getId())).thenReturn(Optional.of(bankEmployee));
        when(bankUploadRepository.existsByFileChecksum(anyString())).thenReturn(false);
        when(bankUploadRepository.save(any(BankUpload.class))).thenAnswer(i -> {
            BankUpload u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        BankUploadResponseDto response = service.uploadTransactions(file, employeePrincipal, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(BankUploadStatus.PENDING);
        assertThat(response.getRowCount()).isEqualTo(2);

        verify(bankUploadRepository).save(any(BankUpload.class));
        verify(auditService).log(eq(bankEmployee.getId()), eq("BANK_EMPLOYEE"),
                eq("UPLOAD_BANK_TRANSACTIONS"), eq("BANK_UPLOAD"), anyString(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void rejectsDuplicateUploadChecksum() {
        byte[] content = "utr,sender,receiver,amount,timestamp\nUTR1,A,B,10.0,2026-09-22T00:00:00Z".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "tx.csv", "text/csv", content);
        UserPrincipal principal = UserPrincipal.fromUser(bankEmployee);

        when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
        when(userRepository.findById(bankEmployee.getId())).thenReturn(Optional.of(bankEmployee));
        when(bankUploadRepository.existsByFileChecksum(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.uploadTransactions(file, principal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Duplicate file");
    }

    @Test
    void enforcesTwoPersonRuleWhereUploaderCannotReview() {
        BankUpload upload = new BankUpload(testBank, bankEmployee, "dummy.csv", "chk123", 1);
        upload.setId(UUID.randomUUID());
        upload.setStatus(BankUploadStatus.PENDING);

        when(bankUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));

        // Employee attempting to review their own upload
        UserPrincipal uploaderPrincipal = UserPrincipal.fromUser(bankEmployee);
        ReviewUploadRequest request = new ReviewUploadRequest(BankUploadStatus.APPROVED, "Self approved");

        assertThatThrownBy(() -> service.reviewUpload(upload.getId(), request, uploaderPrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Two-person approval violation");
    }

    @Test
    void rejectsManagerFromDifferentBank() {
        Bank otherBank = new Bank();
        otherBank.setId(UUID.randomUUID());
        otherBank.setCode("SBI");

        User otherManager = new User();
        otherManager.setId(UUID.randomUUID());
        otherManager.setRole(UserRole.BANK_MANAGER);
        otherManager.setBank(otherBank);

        BankUpload upload = new BankUpload(testBank, bankEmployee, "dummy.csv", "chk123", 1);
        upload.setId(UUID.randomUUID());
        upload.setStatus(BankUploadStatus.PENDING);

        when(bankUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));

        UserPrincipal otherPrincipal = UserPrincipal.fromUser(otherManager);
        ReviewUploadRequest request = new ReviewUploadRequest(BankUploadStatus.APPROVED, "Cross-bank review");

        assertThatThrownBy(() -> service.reviewUpload(upload.getId(), request, otherPrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Bank manager can only review uploads for their own bank");
    }

    @Test
    void rejectUploadUpdatesStatusAndAudit() {
        BankUpload upload = new BankUpload(testBank, bankEmployee, "dummy.csv", "chk123", 1);
        upload.setId(UUID.randomUUID());
        upload.setStatus(BankUploadStatus.PENDING);

        when(bankUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(userRepository.findById(bankManager.getId())).thenReturn(Optional.of(bankManager));
        when(bankUploadRepository.save(any(BankUpload.class))).thenAnswer(i -> i.getArgument(0));

        UserPrincipal managerPrincipal = UserPrincipal.fromUser(bankManager);
        ReviewUploadRequest request = new ReviewUploadRequest(BankUploadStatus.REJECTED, "Invalid customer records");

        BankUploadResponseDto result = service.reviewUpload(upload.getId(), request, managerPrincipal, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(BankUploadStatus.REJECTED);
        assertThat(result.getReviewNote()).isEqualTo("Invalid customer records");
        verify(auditService).log(eq(bankManager.getId()), eq("BANK_MANAGER"),
                eq("REJECT_BANK_UPLOAD"), eq("BANK_UPLOAD"), eq(upload.getId().toString()), anyString(), eq("127.0.0.1"));
        verifyNoInteractions(transactionRepository);
    }
}
