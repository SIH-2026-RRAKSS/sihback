package com.sih.dataservice.complaints.service;

import com.sih.dataservice.complaints.repository.ComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReferenceNumberService {

    private final ComplaintRepository complaintRepository;
    private final Clock clock;
    private final AtomicLong counter = new AtomicLong(0);
    private int currentYear = 0;

    public ReferenceNumberService(ComplaintRepository complaintRepository, Clock clock) {
        this.complaintRepository = complaintRepository;
        this.clock = clock;
    }

    /**
     * Generates a sequential reference per year in format: CC-YYYY-000001 (FR-CMP-1).
     */
    @Transactional(readOnly = true)
    public synchronized String generateReference() {
        int year = Year.now(clock).getValue();

        if (year != currentYear) {
            currentYear = year;
            var startOfYear = Year.of(year).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            var endOfYear = Year.of(year + 1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            long count = complaintRepository.countByCreatedAtBetween(startOfYear, endOfYear);
            counter.set(count);
        }

        long nextNum = counter.incrementAndGet();
        return String.format("CC-%d-%06d", year, nextNum);
    }
}
