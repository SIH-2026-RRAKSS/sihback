package com.sih.dataservice.complaints.service;

import com.sih.dataservice.complaints.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceNumberServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    private Clock fixedClock;
    private ReferenceNumberService service;

    @BeforeEach
    void setUp() {
        // 2026-09-22T00:00:00Z
        Instant instant = Instant.parse("2026-09-22T00:00:00Z");
        fixedClock = Clock.fixed(instant, ZoneOffset.UTC);
        service = new ReferenceNumberService(complaintRepository, fixedClock);
    }

    @Test
    void generatesCorrectSequentialReferenceFormat() {
        when(complaintRepository.countByCreatedAtBetween(any(Instant.class), any(Instant.class))).thenReturn(42L);

        String ref1 = service.generateReference();
        String ref2 = service.generateReference();

        assertThat(ref1).isEqualTo("CC-2026-000043");
        assertThat(ref2).isEqualTo("CC-2026-000044");
    }
}
