package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Scheduled worker for running daily company formation anniversary greetings and retry processing.
 * Configured by default to execute daily at 09:00 IST (Asia/Kolkata).
 */
@Service
public class AnniversarySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AnniversarySchedulerService.class);

    private final AnniversaryConfig anniversaryConfig;
    private final AnniversaryMatchingService matchingService;
    private final AnniversaryGreetingService greetingService;
    private final UserRepository userRepository;

    public AnniversarySchedulerService(AnniversaryConfig anniversaryConfig,
                                     AnniversaryMatchingService matchingService,
                                     AnniversaryGreetingService greetingService,
                                     UserRepository userRepository) {
        this.anniversaryConfig = anniversaryConfig;
        this.matchingService = matchingService;
        this.greetingService = greetingService;
        this.userRepository = userRepository;
    }

    public static class ExecutionSummary {
        private final LocalDate executionDate;
        private int totalClientsScanned = 0;
        private int eligibleToday = 0;
        private int processedSuccessfully = 0;
        private int failedOrIneligible = 0;
        private long durationMillis = 0;

        public ExecutionSummary(LocalDate executionDate) {
            this.executionDate = executionDate;
        }

        public LocalDate getExecutionDate() { return executionDate; }
        public int getTotalClientsScanned() { return totalClientsScanned; }
        public int getEligibleToday() { return eligibleToday; }
        public int getProcessedSuccessfully() { return processedSuccessfully; }
        public int getFailedOrIneligible() { return failedOrIneligible; }
        public long getDurationMillis() { return durationMillis; }
    }

    /**
     * Daily scheduled task triggered at 09:00 Asia/Kolkata (configurable via cron).
     */
    @Scheduled(cron = "${whatsapp.anniversary.scheduler.cron:0 0 9 * * *}",
               zone = "${whatsapp.anniversary.scheduler.timezone:Asia/Kolkata}")
    public void executeDailyAnniversaryJob() {
        if (!anniversaryConfig.isSchedulerEnabled()) {
            log.info("[ANNIVERSARY-SCHEDULER] Daily scheduler is currently DISABLED via configuration. Skipping execution.");
            return;
        }

        log.info("[ANNIVERSARY-SCHEDULER] Starting automated daily company formation anniversary greeting job...");
        executeAnniversaryJobForDate(matchingService.getCurrentBusinessDate(), "SCHEDULER");
    }

    /**
     * Periodic retry job checking for any retry-pending notifications whose backoff window has elapsed.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedDelayString = "${whatsapp.anniversary.retry.check-interval-ms:600000}", initialDelay = 60000)
    public void executePendingRetriesJob() {
        if (!anniversaryConfig.isSchedulerEnabled()) {
            return;
        }
        try {
            int retried = greetingService.retryPendingGreetings();
            if (retried > 0) {
                log.info("[ANNIVERSARY-SCHEDULER] Executed {} pending retries.", retried);
            }
        } catch (Exception e) {
            log.error("[ANNIVERSARY-SCHEDULER] Exception during pending retries execution: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes the anniversary workflow for a specific business date with batched pagination.
     */
    public ExecutionSummary executeAnniversaryJobForDate(LocalDate businessDate, String triggeredBy) {
        Instant start = Instant.now();
        ExecutionSummary summary = new ExecutionSummary(businessDate);

        log.info("[ANNIVERSARY-SCHEDULER] Processing anniversaries for business date: {} (Triggered by: {})",
                businessDate, triggeredBy);

        int pageSize = 100;
        int pageIndex = 0;
        Page<User> page;

        do {
            page = userRepository.findAll(PageRequest.of(pageIndex, pageSize));
            for (User client : page.getContent()) {
                summary.totalClientsScanned++;
                try {
                    if (client.getClientType() == ClientType.COMPANY &&
                            client.getFormationDate() != null &&
                            matchingService.isAnniversaryToday(client.getFormationDate(), businessDate)) {
                        summary.eligibleToday++;

                        var resultOpt = greetingService.processClientGreeting(
                                client, businessDate, false, triggeredBy);

                        if (resultOpt.isPresent()) {
                            summary.processedSuccessfully++;
                        } else {
                            summary.failedOrIneligible++;
                        }
                    }
                } catch (Exception e) {
                    summary.failedOrIneligible++;
                    log.error("[ANNIVERSARY-SCHEDULER] Unexpected error processing client ID '{}': {}",
                            client.getUserId(), e.getMessage(), e);
                }
            }
            pageIndex++;
        } while (page.hasNext());

        summary.durationMillis = Duration.between(start, Instant.now()).toMillis();

        log.info("[ANNIVERSARY-SCHEDULER] Completed anniversary run for date {}. Total Scanned: {}, Eligible: {}, Succeeded: {}, Skipped/Failed: {}, Duration: {} ms",
                businessDate, summary.totalClientsScanned, summary.eligibleToday,
                summary.processedSuccessfully, summary.failedOrIneligible, summary.durationMillis);

        return summary;
    }
}
