package com.sih.dataservice.notify.scheduler;

import com.sih.dataservice.notify.service.NotificationSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background job that triggers notification delivery periodically (FR-WA-4).
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationSenderService senderService;

    public NotificationScheduler(NotificationSenderService senderService) {
        this.senderService = senderService;
    }

    /**
     * Polls and processes pending notifications every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void runNotificationSweep() {
        try {
            int dispatched = senderService.processPendingNotifications();
            if (dispatched > 0) {
                log.info("Dispatched {} queued notification(s)", dispatched);
            }
        } catch (Exception e) {
            log.error("Error during scheduled notification sweep: {}", e.getMessage(), e);
        }
    }
}
