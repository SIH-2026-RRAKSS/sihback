package com.sih.dataservice.freeze.scheduler;

import com.sih.dataservice.freeze.service.FreezeRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class FreezeSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(FreezeSlaScheduler.class);

    private final FreezeRequestService freezeRequestService;
    private final Clock clock;

    public FreezeSlaScheduler(FreezeRequestService freezeRequestService, Clock clock) {
        this.freezeRequestService = freezeRequestService;
        this.clock = clock;
    }

    /**
     * Periodic SLA evaluation: Day 5 reminder, Day 7 escalation.
     */
    @Scheduled(fixedDelayString = "${sla.freeze.check-interval-ms:60000}")
    public Map<String, Integer> runSlaCheck() {
        Instant now = Instant.now(clock);
        log.debug("Running scheduled freeze SLA check at {}", now);
        Map<String, Integer> results = freezeRequestService.checkSlaBreachesAndReminders(now);
        if (results.getOrDefault("remindersSent", 0) > 0 || results.getOrDefault("escalationsTriggered", 0) > 0) {
            log.info("SLA check completed: {}", results);
        }
        return results;
    }
}
