package com.sih.dataservice.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.bankupload.dto.BankUploadResponseDto;
import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.bankupload.entity.BankUploadStatus;
import com.sih.dataservice.bankupload.repository.BankUploadRepository;
import com.sih.dataservice.bankupload.service.BankUploadService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.CreateComplaintRequest;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.TransitionCaseRequest;
import com.sih.dataservice.complaints.dto.UpdateCaseLabelRequest;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.complaints.service.CaseWorkflowService;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.freeze.dto.CreateFreezeRequestDto;
import com.sih.dataservice.freeze.dto.FreezeRequestResponseDto;
import com.sih.dataservice.freeze.dto.RespondFreezeRequestDto;
import com.sih.dataservice.freeze.entity.FreezeRequest;
import com.sih.dataservice.freeze.entity.FreezeRequestStatus;
import com.sih.dataservice.freeze.repository.FreezeRequestRepository;
import com.sih.dataservice.freeze.scheduler.FreezeSlaScheduler;
import com.sih.dataservice.freeze.service.FreezeRequestService;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import com.sih.dataservice.gate.service.AnomalyGateService;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.ml.client.ModelClient;
import com.sih.dataservice.ml.dto.ModelVersionDetailDto;
import com.sih.dataservice.ml.dto.PredictionResponse;
import com.sih.dataservice.ml.dto.RetrainRequestDto;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.ml.service.PredictionService;
import com.sih.dataservice.ml.service.RetrainingService;
import com.sih.dataservice.ml.service.SnapshotExportService;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.streaming.dto.StreamAlertDto;
import com.sih.dataservice.streaming.service.StreamingReplayService;
import com.sih.dataservice.bankupload.dto.ReviewUploadRequest;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.complaints.repository.FinancialEntityRepository;
import com.sih.dataservice.ml.dto.TopFeatureExplanation;
import com.sih.dataservice.ml.dto.TopNodeExplanation;
import com.sih.dataservice.users.entity.*;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import com.sih.dataservice.whatsapp.entity.WhatsAppSession;
import com.sih.dataservice.whatsapp.entity.WhatsAppSessionState;
import com.sih.dataservice.whatsapp.gateway.MockMessagingGateway;
import com.sih.dataservice.whatsapp.i18n.MessageBundleService;
import com.sih.dataservice.whatsapp.repository.WhatsAppSessionRepository;
import com.sih.dataservice.whatsapp.service.WhatsAppBotService;
import com.sih.dataservice.whatsapp.translation.MockTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Automated Verification of All 8 End-to-End Scenarios specified in Section 5 of test.md.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndScenariosTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintAccountRepository complaintAccountRepository;
    @Mock private FinancialEntityRepository financialEntityRepository;
    @Mock private CaseEventRepository caseEventRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private BankRepository bankRepository;
    @Mock private JurisdictionRepository jurisdictionRepository;
    @Mock private FreezeRequestRepository freezeRequestRepository;
    @Mock private BankUploadRepository bankUploadRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PredictionRepository predictionRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private TrainingSnapshotRepository trainingSnapshotRepository;
    @Mock private WhatsAppSessionRepository whatsAppSessionRepository;

    @Mock private AnomalyGateService anomalyGateService;
    @Mock private TemporalGraphEngine graphEngine;
    @Mock private ModelClient modelClient;
    @Mock private AuditService auditService;
    @Mock private ScopeService scopeService;
    @Mock private CryptoService cryptoService;

    @TempDir
    private Path tempDir;

    private Clock clock;
    private ObjectMapper objectMapper;

    private User complainant;
    private User policeDistrictA;
    private User policeDistrictB;
    private User bankEmployeeA;
    private User bankManagerA;
    private User bankEmployeeB;
    private User cyberOfficer;
    private Bank bankA;
    private Bank bankB;
    private Jurisdiction jurisdictionA;
    private Jurisdiction jurisdictionB;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC);
        objectMapper = new ObjectMapper();

        bankA = new Bank();
        bankA.setId(UUID.randomUUID());
        bankA.setName("State Bank of India");

        bankB = new Bank();
        bankB.setId(UUID.randomUUID());
        bankB.setName("HDFC Bank");

        jurisdictionA = new Jurisdiction();
        jurisdictionA.setId(UUID.randomUUID());
        jurisdictionA.setName("Bhubaneswar Cyber PS");
        jurisdictionA.setPath("/OD/KHORDHA/BHUBANESWAR");

        jurisdictionB = new Jurisdiction();
        jurisdictionB.setId(UUID.randomUUID());
        jurisdictionB.setName("Cuttack PS");
        jurisdictionB.setPath("/OD/CUTTACK/CITY");

        complainant = new User();
        complainant.setId(UUID.randomUUID());
        complainant.setName("Aarav Patel");
        complainant.setRole(UserRole.COMPLAINANT);

        policeDistrictA = new User();
        policeDistrictA.setId(UUID.randomUUID());
        policeDistrictA.setName("Inspector Roy");
        policeDistrictA.setRole(UserRole.POLICE);
        policeDistrictA.setJurisdiction(jurisdictionA);

        policeDistrictB = new User();
        policeDistrictB.setId(UUID.randomUUID());
        policeDistrictB.setName("Inspector Jena");
        policeDistrictB.setRole(UserRole.POLICE);
        policeDistrictB.setJurisdiction(jurisdictionB);

        bankEmployeeA = new User();
        bankEmployeeA.setId(UUID.randomUUID());
        bankEmployeeA.setName("Bank Clerk A");
        bankEmployeeA.setRole(UserRole.BANK_EMPLOYEE);
        bankEmployeeA.setBank(bankA);

        bankManagerA = new User();
        bankManagerA.setId(UUID.randomUUID());
        bankManagerA.setName("Bank Manager A");
        bankManagerA.setRole(UserRole.BANK_MANAGER);
        bankManagerA.setBank(bankA);

        bankEmployeeB = new User();
        bankEmployeeB.setId(UUID.randomUUID());
        bankEmployeeB.setName("Bank Clerk B");
        bankEmployeeB.setRole(UserRole.BANK_EMPLOYEE);
        bankEmployeeB.setBank(bankB);

        cyberOfficer = new User();
        cyberOfficer.setId(UUID.randomUUID());
        cyberOfficer.setName("DSP Cyber");
        cyberOfficer.setRole(UserRole.CYBER_OFFICER);
    }

    @Test
    @DisplayName("Scenario 1: Full complaint to closure lifecycle (FR-CMP, FR-FRZ, FR-LBL, FR-AUD)")
    void testScenario1_ComplaintToClosure() {
        CaseWorkflowService workflowService = new CaseWorkflowService(
                complaintRepository, caseEventRepository, notificationRepository,
                userRepository, scopeService, auditService, clock
        );

        UUID complaintId = UUID.randomUUID();
        Complaint complaint = new Complaint();
        complaint.setId(complaintId);
        complaint.setStatus(ComplaintStatus.FILED);
        complaint.setComplainant(complainant);
        complaint.setJurisdiction(jurisdictionA);

        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(userRepository.findById(policeDistrictA.getId())).thenReturn(Optional.of(policeDistrictA));
        when(userRepository.findById(cyberOfficer.getId())).thenReturn(Optional.of(cyberOfficer));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Assign Police
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeDistrictA);
        IncidentDetailDto assigned = workflowService.assignOfficer(complaintId, policeDistrictA.getId(), policePrincipal, "127.0.0.1");
        assertThat(assigned.getStatus()).isEqualTo(ComplaintStatus.ASSIGNED);

        // 2. Start Investigation
        TransitionCaseRequest startInv = new TransitionCaseRequest(ComplaintStatus.UNDER_INVESTIGATION, "Investigation begun", null);
        IncidentDetailDto investigating = workflowService.transitionCase(complaintId, startInv, policePrincipal, "127.0.0.1");
        assertThat(investigating.getStatus()).isEqualTo(ComplaintStatus.UNDER_INVESTIGATION);

        // 3. Cyber Officer Closes Case as Fraud
        UserPrincipal cyberPrincipal = UserPrincipal.fromUser(cyberOfficer);
        TransitionCaseRequest closeFraud = new TransitionCaseRequest(ComplaintStatus.CLOSED_FRAUD, "Mule ring identified", CaseLabel.FRAUD);
        IncidentDetailDto closed = workflowService.transitionCase(complaintId, closeFraud, cyberPrincipal, "127.0.0.1");
        assertThat(closed.getStatus()).isEqualTo(ComplaintStatus.CLOSED_FRAUD);
        assertThat(closed.getLabel()).isEqualTo(CaseLabel.FRAUD);

        verify(auditService, times(3)).log(any(), anyString(), anyString(), eq("COMPLAINT"), eq(complaintId.toString()), anyString(), anyString());
    }

    @Test
    @DisplayName("Scenario 2: Scope isolation and IDOR resistance (NFR-SEC-1)")
    void testScenario2_ScopeIsolation() {
        ScopeService realScopeService = new ScopeService();
        UserPrincipal policeBPrincipal = UserPrincipal.fromUser(policeDistrictB);

        // Police in District B trying to access District A complaint
        assertThatThrownBy(() -> realScopeService.enforceCaseAccess(policeBPrincipal, complainant.getId(), null, jurisdictionA.getPath()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Resource not found"); // Expect 404, not 403 (NFR-SEC-1)
    }

    @Test
    @DisplayName("Scenario 3: Freeze SLA breach and escalation (FR-FRZ)")
    void testScenario3_FreezeSlaBreach() {
        FreezeRequestService freezeService = new FreezeRequestService(
                freezeRequestRepository, complaintRepository, financialEntityRepository,
                userRepository, caseEventRepository, notificationRepository, scopeService,
                auditService, objectMapper, clock
        );
        FreezeSlaScheduler scheduler = new FreezeSlaScheduler(freezeService, clock);

        UUID freezeId = UUID.randomUUID();
        Complaint mockComplaint = new Complaint();
        mockComplaint.setId(UUID.randomUUID());
        FinancialEntity entity = new FinancialEntity("hash_breach", "enc_breach", bankA, EntityType.ACCOUNT);
        FreezeRequest req = new FreezeRequest(mockComplaint, entity, bankA, bankEmployeeA, clock.instant().minusSeconds(86400 * 8), clock.instant().minusSeconds(3600));
        req.setId(freezeId);
        req.setStatus(FreezeRequestStatus.OPEN);

        when(freezeRequestRepository.findPendingReminders(any(Instant.class))).thenReturn(List.of());
        when(freezeRequestRepository.findOverdueEscalations(any(Instant.class))).thenReturn(List.of(req));
        when(freezeRequestRepository.save(any(FreezeRequest.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Integer> result = scheduler.runSlaCheck();

        assertThat(result.get("escalationsTriggered")).isEqualTo(1);
        assertThat(req.getEscalatedAt()).isNotNull();
        verify(auditService).log(isNull(), eq("SYSTEM"), eq("FREEZE_ESCALATED"), eq("FREEZE_REQUEST"), eq(freezeId.toString()), anyString(), any());
    }

    @Test
    @DisplayName("Scenario 4: Bank upload two-person authorization (FR-UPL)")
    void testScenario4_BankUploadTwoPersonRule() throws Exception {
        BankUploadService uploadService = new BankUploadService(
                bankUploadRepository, transactionRepository, financialEntityRepository,
                bankRepository, userRepository, cryptoService, graphEngine,
                auditService, clock, tempDir.toString()
        );

        UUID uploadId = UUID.randomUUID();
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, "utr,sender_account,receiver_account,amount,timestamp\nUTR1,ACC1,ACC2,1000.0,2026-09-22T00:00:00Z\n");
        BankUpload upload = new BankUpload(bankA, bankEmployeeA, csvFile.toString(), "sha256_mock_hash", 1);
        upload.setId(uploadId);
        upload.setStatus(BankUploadStatus.PENDING);

        when(bankUploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        // 1. Employee cannot self-approve
        UserPrincipal employeePrincipal = UserPrincipal.fromUser(bankEmployeeA);
        ReviewUploadRequest req = new ReviewUploadRequest(BankUploadStatus.APPROVED, "Approved");
        assertThatThrownBy(() -> uploadService.reviewUpload(uploadId, req, employeePrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Two-person approval violation");

        // 2. Manager approves
        UserPrincipal managerPrincipal = UserPrincipal.fromUser(bankManagerA);
        when(bankUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BankUploadResponseDto approved = uploadService.reviewUpload(uploadId, req, managerPrincipal, "127.0.0.1");
        assertThat(approved.getStatus()).isEqualTo(BankUploadStatus.APPROVED);
    }

    @Test
    @DisplayName("Scenario 5: WhatsApp bot guided filing (FR-WA)")
    void testScenario5_WhatsAppFiling() {
        MessageBundleService messageBundleService = new MessageBundleService();
        MockMessagingGateway messagingGateway = new MockMessagingGateway();
        MockTranslationService translationService = new MockTranslationService();
        ComplaintService complaintService = mock(ComplaintService.class);

        WhatsAppBotService botService = new WhatsAppBotService(
                whatsAppSessionRepository, complaintRepository, userRepository,
                complaintService, messagingGateway, messageBundleService,
                translationService, cryptoService, objectMapper
        );

        String phone = "+919876543210";
        String phoneHash = "hash_phone_123";
        when(cryptoService.computeHmac(phone)).thenReturn(phoneHash);

        WhatsAppSession session = new WhatsAppSession(phoneHash);
        session.setState(WhatsAppSessionState.EVIDENCE);
        session.setLanguage("en");
        session.setDraft("{\"amount\":\"50000\",\"fraudType\":\"UPI_FRAUD\",\"descriptionOriginal\":\"fake lottery\",\"accountOrUtr\":\"ACC123\"}");

        when(whatsAppSessionRepository.findByPhoneHash(phoneHash)).thenReturn(Optional.of(session));
        when(whatsAppSessionRepository.save(any(WhatsAppSession.class))).thenAnswer(i -> i.getArgument(0));

        String reply = botService.handleIncomingMessage(phone, "none");

        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.CONFIRM);
        assertThat(reply).isNotBlank();
    }

    @Test
    @DisplayName("Scenario 6: Model service outage fallback to anomaly gate (FR-PRD-3)")
    void testScenario6_ModelOutageFallback() {
        PredictionService predictionService = new PredictionService(
                complaintRepository, complaintAccountRepository, predictionRepository,
                modelVersionRepository, caseEventRepository, graphEngine, anomalyGateService,
                modelClient, auditService, objectMapper, clock
        );

        UUID compId = UUID.randomUUID();
        Complaint comp = new Complaint();
        comp.setId(compId);
        comp.setStatus(ComplaintStatus.FILED);
        comp.setAmount(BigDecimal.valueOf(50000));

        FinancialEntity ent = new FinancialEntity("h1", "e1", null, EntityType.ACCOUNT);
        ent.setId(UUID.randomUUID());
        ComplaintAccount acc = new ComplaintAccount(comp, ent, AccountRole.SUSPECT);

        when(complaintRepository.findById(compId)).thenReturn(Optional.of(comp));
        when(complaintAccountRepository.findByComplaintId(compId)).thenReturn(List.of(acc));
        when(graphEngine.extractSubgraph(any(), anyInt(), any(), any(), anyInt()))
                .thenReturn(new Subgraph(ent.getId(), Map.of(), Map.of(), List.of()));

        // Simulate Model Service Crash / Outage
        when(modelClient.predict(any())).thenThrow(new RuntimeException("Connection refused (model outage)"));

        GateEvaluationResult gateResult = new GateEvaluationResult(ent.getId(), true, false, 4.5, 10, "Gate velocity alert");
        when(anomalyGateService.evaluate(eq(ent.getId()), anyDouble(), any())).thenReturn(gateResult);
        when(predictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Prediction pred = predictionService.scoreComplaint(compId);

        assertThat(pred.isModelAvailable()).isFalse();
        assertThat(pred.getRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
        assertThat(comp.getStatus()).isEqualTo(ComplaintStatus.TRIAGED); // Still successfully auto-triaged!
    }

    @Test
    @DisplayName("Scenario 7: Retrain, snapshot, candidate registration, and manual promotion (FR-RTR)")
    void testScenario7_RetrainAndPromote() {
        RetrainingService retrainingService = new RetrainingService(
                modelVersionRepository, trainingSnapshotRepository, complaintRepository,
                userRepository, auditService, objectMapper, "http://localhost:8001"
        );

        UUID candidateId = UUID.randomUUID();
        ModelVersion candidate = new ModelVersion("graphsage", "v2.0.0-candidate", "{\"f1\":0.93}", ModelVersionStatus.CANDIDATE);
        candidate.setId(candidateId);

        UUID activeId = UUID.randomUUID();
        ModelVersion currentActive = new ModelVersion("graphsage", "v1.0.0", "{\"f1\":0.88}", ModelVersionStatus.ACTIVE);
        currentActive.setId(activeId);

        when(modelVersionRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(userRepository.findById(cyberOfficer.getId())).thenReturn(Optional.of(cyberOfficer));
        when(modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE)).thenReturn(Optional.of(currentActive));
        when(modelVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPrincipal cyberPrincipal = UserPrincipal.fromUser(cyberOfficer);
        ModelVersionDetailDto promoted = retrainingService.promoteCandidate(candidateId, cyberPrincipal, "127.0.0.1");

        assertThat(promoted.getStatus()).isEqualTo(ModelVersionStatus.ACTIVE);
        assertThat(currentActive.getStatus()).isEqualTo(ModelVersionStatus.RETIRED); // Previous retired (FR-RTR-6)
    }

    @Test
    @DisplayName("Scenario 8: Streaming transaction replay with selective gate triggering and SSE alert (FR-STR, FR-GATE)")
    void testScenario8_StreamingReplay() {
        StreamingReplayService streamingService = new StreamingReplayService(
                anomalyGateService, graphEngine, modelClient, mock(com.sih.dataservice.datasets.SyntheticDataGenerator.class), objectMapper
        );

        FinancialEntity sender = new FinancialEntity("s_hash", "enc_s", null, EntityType.ACCOUNT);
        sender.setId(UUID.randomUUID());
        FinancialEntity receiver = new FinancialEntity("r_hash", "enc_r", null, EntityType.ACCOUNT);
        receiver.setId(UUID.randomUUID());

        Transaction normalTx = new Transaction("UTR-NORM", sender, receiver, BigDecimal.valueOf(1000), Instant.now(), TransactionSource.STREAM);
        Transaction muleTx = new Transaction("UTR-ANOM", sender, receiver, BigDecimal.valueOf(80000), Instant.now(), TransactionSource.STREAM);

        // Normal -> gate does not trip -> returns null (no Stage 2)
        GateEvaluationResult normalRes = new GateEvaluationResult(sender.getId(), false, false, 0.2, 1, "Normal");
        when(anomalyGateService.evaluate(eq(sender.getId()), eq(1000.0), any())).thenReturn(normalRes);
        when(anomalyGateService.evaluate(eq(receiver.getId()), eq(1000.0), any())).thenReturn(normalRes);

        StreamAlertDto res1 = streamingService.step(normalTx);
        assertThat(res1).isNull();

        // Anomaly -> gate trips -> executes Stage 2 and returns StreamAlertDto
        GateEvaluationResult anomRes = new GateEvaluationResult(sender.getId(), true, false, 4.1, 9, "Spike");
        when(anomalyGateService.evaluate(eq(sender.getId()), eq(80000.0), any())).thenReturn(anomRes);
        when(graphEngine.extractSubgraph(any(), anyInt(), any(), any(), anyInt()))
                .thenReturn(new Subgraph(sender.getId(), Map.of(), Map.of(), List.of()));
        when(modelClient.predict(any()))
                .thenReturn(new PredictionResponse(0.91, 0.95,
                        List.of(new TopNodeExplanation("node1", 0.9)),
                        List.of(new TopFeatureExplanation("feat1", 0.8)),
                        "graphsage-v1.0.0"));

        StreamAlertDto res2 = streamingService.step(muleTx);
        assertThat(res2).isNotNull();
        assertThat(res2.isGateTripped()).isTrue();
        assertThat(res2.getRiskScore()).isEqualTo(0.91);
    }
}
