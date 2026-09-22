package com.sih.dataservice.complaints.service;

import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.EvidenceDto;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.Evidence;
import com.sih.dataservice.complaints.repository.EvidenceRepository;
import com.sih.dataservice.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceStorageServiceTest {

    @Mock
    private EvidenceRepository evidenceRepository;

    @TempDir
    Path tempDir;

    private EvidenceStorageService service;
    private Complaint testComplaint;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new EvidenceStorageService(evidenceRepository, tempDir.toString());

        testComplaint = new Complaint();
        testComplaint.setId(UUID.randomUUID());

        testUser = new User();
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void successfullyStoresValidEvidenceFile() {
        byte[] content = "fake-pdf-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", content);

        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        EvidenceDto dto = service.storeEvidence(testComplaint, testUser, file);

        assertThat(dto).isNotNull();
        assertThat(dto.getMimeType()).isEqualTo("application/pdf");
        assertThat(dto.getSizeBytes()).isEqualTo(content.length);
        assertThat(dto.getSha256()).isNotNull();
    }

    @Test
    void rejectsUnsupportedMimeType() {
        MockMultipartFile scriptFile = new MockMultipartFile(
                "file", "exploit.sh", "application/x-sh", "rm -rf /".getBytes());

        assertThatThrownBy(() -> service.storeEvidence(testComplaint, testUser, scriptFile))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.storeEvidence(testComplaint, testUser, emptyFile))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsFileExceeding10MB() {
        byte[] bigContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "huge.pdf", "application/pdf", bigContent);

        assertThatThrownBy(() -> service.storeEvidence(testComplaint, testUser, bigFile))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exceeds 10MB");
    }
}
