package com.sih.dataservice;

import com.sih.dataservice.audit.repository.AuditLogRepository;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DataServiceApplicationTests {

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private BankRepository bankRepository;

    @MockBean
    private JurisdictionRepository jurisdictionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.sih.dataservice.complaints.repository.ComplaintRepository complaintRepository;

    @MockBean
    private com.sih.dataservice.complaints.repository.ComplaintAccountRepository complaintAccountRepository;

    @MockBean
    private com.sih.dataservice.complaints.repository.FinancialEntityRepository financialEntityRepository;

    @MockBean
    private com.sih.dataservice.complaints.repository.CaseEventRepository caseEventRepository;

    @MockBean
    private com.sih.dataservice.complaints.repository.EvidenceRepository evidenceRepository;

    @MockBean
    private com.sih.dataservice.notify.repository.NotificationRepository notificationRepository;

    @MockBean
    private com.sih.dataservice.bankupload.repository.BankUploadRepository bankUploadRepository;

    @MockBean
    private com.sih.dataservice.graph.repository.TransactionRepository transactionRepository;

    @MockBean
    private com.sih.dataservice.ml.repository.ModelVersionRepository modelVersionRepository;

    @MockBean
    private com.sih.dataservice.ml.repository.PredictionRepository predictionRepository;

    @Test
    void contextLoads() {
    }

}
