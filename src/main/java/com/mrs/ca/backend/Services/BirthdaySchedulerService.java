package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.Founder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import com.mrs.ca.backend.Repositories.FounderRepository;

/**
 * Daily scheduled worker that sends WhatsApp birthday greetings to eligible founders.
 *
 * <h3>Schedule</h3>
 * <p>Runs at 09:00 {@code Asia/Kolkata} by default (configurable via
 * {@code whatsapp.birthday.scheduler.cron} and {@code whatsapp.birthday.scheduler.timezone}).</p>
 *
 * <h3>Leap-day policy for Feb 29 birthdays</h3>
 * <p>In non-leap years, founders born on February 29 are greeted according to the
 * {@code whatsapp.birthday.leap-day-policy} setting:
 * <ul>
 *   <li>{@code FEB_28} (default): greeting is sent on February 28.</li>
 *   <li>{@code MARCH_1}: greeting is sent on March 1.</li>
 * </ul>
 * </p>
 *
 * <h3>Duplicate prevention</h3>
 * <p>Each (founderId, birthdayYear, FOUNDER_BIRTHDAY) tuple is protected by a compound
 * unique index in {@code anniversary_notification_logs}. The service performs an atomic
 * claim before dispatching. Concurrent scheduler instances and restarts are safe.</p>
 *
 * <h3>Enabling the scheduler</h3>
 * <p>Set {@code WHATSAPP_BIRTHDAY_SCHEDULER_ENABLED=true} in the environment.
 * The scheduler defaults to {@code false} to prevent accidental production sends
 * before the {@code founder_birthday_greeting} template is approved in Meta WhatsApp Manager.</p>
 */
@Service
public class BirthdaySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BirthdaySchedulerService.class);

    private final BirthdayConfig birthdayConfig;
    private final BirthdayGreetingService greetingService;
    private final FounderRepository founderRepository;
    private final Clock clock;

    public BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                                    BirthdayGreetingService greetingService,
                                    FounderRepository founderRepository) {
        this.birthdayConfig = birthdayConfig;
        this.greetingService = greetingService;
        this.founderRepository = founderRepository;
        this.clock = Clock.system(birthdayConfig.getZoneId());
    }

    /** Package-private constructor for testing with a custom clock. */
    BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                             BirthdayGreetingService greetingService,
                             FounderRepository founderRepository,
                             Clock clock) {
        this.birthdayConfig = birthdayConfig;
        this.greetingService = greetingService;
        this.founderRepository = founderRepository;
        this.clock = clock;
    }

    // ─── Immutable execution summary ─────────────────────────────────────────────

    public static class ExecutionSummary {
        private final LocalDate executionDate;
        private int foundersScanned = 0;
        private int eligible = 0;
        private int dispatched = 0;
        private int skippedOrFailed = 0;
        private long durationMillis = 0;

        public ExecutionSummary(LocalDate executionDate) {
            this.executionDate = executionDate;
        }

        public LocalDate getExecutionDate() { return executionDate; }
        public int getFoundersScanned() { return foundersScanned; }
        public int getEligible() { return eligible; }
        public int getDispatched() { return dispatched; }
        public int getSkippedOrFailed() { return skippedOrFailed; }
        public long getDurationMillis() { return durationMillis; }
    }

    // ─── Scheduled jobs ──────────────────────────────────────────────────────────

    /**
     * Daily birthday check at 09:00 Asia/Kolkata (configurable).
     */
    @Scheduled(cron = "${whatsapp.birthday.scheduler.cron:0 0 9 * * *}",
               zone = "${whatsapp.birthday.scheduler.timezone:Asia/Kolkata}")
    public void executeDailyBirthdayJob() {
        if (!birthdayConfig.isSchedulerEnabled()) {
            log.info("[BIRTHDAY-SCHEDULER] Daily scheduler is DISABLED via configuration. Skipping.");
            return;
        }
        log.info("[BIRTHDAY-SCHEDULER] Starting daily founder birthday greeting job...");
        executeBirthdayJobForDate(getCurrentBusinessDate(), "SCHEDULER");
    }

    /**
     * Periodic retry job — runs every 10 minutes, processes any RETRY_PENDING birthday notifications.
     */
    @Scheduled(fixedDelayString = "${whatsapp.birthday.retry.check-interval-ms:600000}", initialDelay = 70000)
    public void executePendingRetriesJob() {
        if (!birthdayConfig.isSchedulerEnabled()) {
            return;
        }
        try {
            int retried = greetingService.retryPendingBirthdayGreetings();
            if (retried > 0) {
                log.info("[BIRTHDAY-SCHEDULER] Executed {} pending birthday retries.", retried);
            }
        } catch (Exception e) {
            log.error("[BIRTHDAY-SCHEDULER] Exception during birthday retry execution: {}", e.getMessage(), e);
        }
    }

    // ─── Core execution logic (package-accessible for admin trigger + tests) ─────

    /**
     * Executes the birthday greeting workflow for the given date.
     *
     * @param businessDate the date to evaluate (month + day matched against founders' DOB)
     * @param triggeredBy  actor string for audit ("SCHEDULER", admin username, etc.)
     * @return a summary of the run
     */
    public ExecutionSummary executeBirthdayJobForDate(LocalDate businessDate, String triggeredBy) {
        Instant start = Instant.now();
        ExecutionSummary summary = new ExecutionSummary(businessDate);

        log.info("[BIRTHDAY-SCHEDULER] Processing birthdays for {} (triggered by: {})", businessDate, triggeredBy);

        // Determine which month/day to query based on leap-day policy
        int[] monthDay = resolveMatchingMonthDay(businessDate);
        int queryMonth = monthDay[0];
        int queryDay = monthDay[1];

        List<Founder> candidates = founderRepository
                .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(
                        queryMonth, queryDay);

        // Also check for leap-day birthdays (Feb 29) being honored on this date
        boolean isFeb28 = businessDate.getMonth() == Month.FEBRUARY && businessDate.getDayOfMonth() == 28;
        boolean isMar1 = businessDate.getMonth() == Month.MARCH && businessDate.getDayOfMonth() == 1;
        boolean isLeapYear = Year.isLeap(businessDate.getYear());

        if (!isLeapYear && isFeb28 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.FEB_28) {
            // Add Feb-29 birthday founders to today's run
            List<Founder> feb29Founders = founderRepository
                    .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29);
            candidates = mergeLists(candidates, feb29Founders);
        } else if (!isLeapYear && isMar1 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.MARCH_1) {
            List<Founder> feb29Founders = founderRepository
                    .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29);
            candidates = mergeLists(candidates, feb29Founders);
        }

        summary.foundersScanned = candidates.size();
        log.info("[BIRTHDAY-SCHEDULER] Found {} candidate founders for {}/{}", candidates.size(), queryMonth, queryDay);

        for (Founder founder : candidates) {
            summary.eligible++;
            try {
                Optional<AnniversaryNotificationLog> result =
                        greetingService.processFounderBirthday(founder, businessDate, false, triggeredBy);
                if (result.isPresent()) {
                    summary.dispatched++;
                } else {
                    summary.skippedOrFailed++;
                }
            } catch (Exception e) {
                summary.skippedOrFailed++;
                log.error("[BIRTHDAY-SCHEDULER] Error processing founder '{}': {}",
                        founder.getId(), e.getMessage(), e);
            }
        }

        summary.durationMillis = Duration.between(start, Instant.now()).toMillis();
        log.info("[BIRTHDAY-SCHEDULER] Completed for {}. Scanned: {}, Eligible: {}, Dispatched: {}, Skipped/Failed: {}, Duration: {} ms",
                businessDate, summary.foundersScanned, summary.eligible,
                summary.dispatched, summary.skippedOrFailed, summary.durationMillis);

        return summary;
    }

    /**
     * Returns the current business date in the configured timezone (Asia/Kolkata by default).
     */
    public LocalDate getCurrentBusinessDate() {
        return LocalDate.now(clock.withZone(birthdayConfig.getZoneId()));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    /**
     * In normal cases returns [month, day] of businessDate.
     * Feb 28/Mar 1 in a non-leap year are handled by augmenting the candidate list
     * with Feb-29 founders directly in executeBirthdayJobForDate().
     */
    private int[] resolveMatchingMonthDay(LocalDate businessDate) {
        return new int[]{businessDate.getMonthValue(), businessDate.getDayOfMonth()};
    }

    private List<Founder> mergeLists(List<Founder> base, List<Founder> extra) {
        if (extra.isEmpty()) return base;
        var merged = new java.util.ArrayList<>(base);
        // Avoid duplicates (e.g. if someone was already in the base list somehow)
        for (Founder f : extra) {
            if (merged.stream().noneMatch(m -> m.getId().equals(f.getId()))) {
                merged.add(f);
            }
        }
        return merged;
    }
}
