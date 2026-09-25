package com.sih.dataservice.dossier.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.dossier.dto.IncidentDossierDto;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierServiceTest {

    @Mock
    private ComplaintService complaintService;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private AuditService auditService;

    private Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneId.of("UTC"));

    private DossierService dossierService;

    private UUID incidentId;
    private UserPrincipal officer;
    private IncidentDetailDto incidentDetail;

    @BeforeEach
    void setUp() {
        dossierService = new DossierService(complaintService, predictionRepository, auditService, clock);

        incidentId = UUID.randomUUID();
        officer = new UserPrincipal(
                UUID.randomUUID(), "cyber_officer_1", "hash", "Officer One",
                UserRole.CYBER_OFFICER, null, "/01", 1, UserStatus.ACTIVE);

        incidentDetail = new IncidentDetailDto();
        incidentDetail.setId(incidentId);
        incidentDetail.setHumanReference("CC-2026-000123");
        incidentDetail.setStatus(ComplaintStatus.UNDER_INVESTIGATION);
        incidentDetail.setFraudType("INVESTMENT_SCAM");
        incidentDetail.setAmount(BigDecimal.valueOf(100000));
        incidentDetail.setIncidentTime(Instant.parse("2026-09-20T10:00:00Z"));
        incidentDetail.setDescriptionOriginal("Victim lured into fake trading app");
        incidentDetail.setAccounts(List.of());
        incidentDetail.setEvents(List.of());
        incidentDetail.setEvidence(List.of());
    }

    @Test
    @DisplayName("getDossier returns structured dossier and logs EXPORT_DOSSIER audit event")
    void testGetDossier() {
        when(complaintService.getIncidentDetail(incidentId, officer)).thenReturn(incidentDetail);

        ModelVersion mv = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);
        Prediction prediction = new Prediction(null, mv, BigDecimal.valueOf(0.9100), BigDecimal.valueOf(0.9500), "{\"features\":[]}", true);
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(incidentId)).thenReturn(Optional.of(prediction));

        IncidentDossierDto result = dossierService.getDossier(incidentId, officer, "192.168.1.100");

        assertThat(result).isNotNull();
        assertThat(result.getIncident().getHumanReference()).isEqualTo("CC-2026-000123");
        assertThat(result.getPrediction()).isNotNull();
        assertThat(result.getPrediction().getRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.9100));
        assertThat(result.getClassificationNotice()).contains("CONFIDENTIAL");

        verify(auditService).log(eq(officer.getId()), eq("CYBER_OFFICER"), eq("EXPORT_DOSSIER"), eq("COMPLAINT"), eq(incidentId.toString()), anyString(), eq("192.168.1.100"));
    }

    @Test
    @DisplayName("exportMarkdown formats dossier in valid Markdown with tables and headers")
    void testExportMarkdown() {
        when(complaintService.getIncidentDetail(incidentId, officer)).thenReturn(incidentDetail);
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(incidentId)).thenReturn(Optional.empty());

        String md = dossierService.exportMarkdown(incidentId, officer, "127.0.0.1");

        assertThat(md).contains("# Incident Dossier: CC-2026-000123");
        assertThat(md).contains("## Risk & ML Assessment");
        assertThat(md).contains("## Case Timeline");
        assertThat(md).contains("CONFIDENTIAL");
    }

    @Test
    @DisplayName("exportHtml formats dossier in styled HTML")
    void testExportHtml() {
        when(complaintService.getIncidentDetail(incidentId, officer)).thenReturn(incidentDetail);
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(incidentId)).thenReturn(Optional.empty());

        String html = dossierService.exportHtml(incidentId, officer, "127.0.0.1");

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("<h1>Incident Dossier: CC-2026-000123</h1>");
        assertThat(html).contains("class=\"badge badge-conf\"");
    }
}
