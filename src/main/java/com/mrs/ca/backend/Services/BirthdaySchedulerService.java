package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.Founder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
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
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.UserRepository;

@Service
public class BirthdaySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BirthdaySchedulerService.class);

    private final BirthdayConfig birthdayConfig;
    private final BirthdayGreetingService greetingService;
    private final FounderRepository founderRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private volatile ExecutionSummary lastExecutionSummary;
    private volatile LocalDateTime lastExecutionTime;
    private volatile String lastExecutionStatus = "NEVER_RUN";
    private final List<String> recentErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Autowired
    public BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                                    BirthdayGreetingService greetingService,
                                    FounderRepository founderRepository,
                                    UserRepository userRepository) {
        this(birthdayConfig, greetingService, founderRepository, userRepository, Clock.system(birthdayConfig.getZoneId()));
    }

    public BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                                    BirthdayGreetingService greetingService,
                                    FounderRepository founderRepository) {
        this(birthdayConfig, greetingService, founderRepository, null, Clock.system(birthdayConfig.getZoneId()));
    }

    /** Package-private constructor for testing with a custom clock without userRepository. */
    BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                             BirthdayGreetingService greetingService,
                             FounderRepository founderRepository,
                             Clock clock) {
        this(birthdayConfig, greetingService, founderRepository, null, clock);
    }

    /** Package-private constructor for testing with a custom clock and userRepository. */
    BirthdaySchedulerService(BirthdayConfig birthdayConfig,
                             BirthdayGreetingService greetingService,
                             FounderRepository founderRepository,
                             UserRepository userRepository,
                             Clock clock) {
        this.birthdayConfig = birthdayConfig;
        this.greetingService = greetingService;
        this.founderRepository = founderRepository;
        this.userRepository = userRepository;
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

        List<Founder> candidates;
        if (birthdayConfig.isRequireExplicitConsent()) {
            candidates = founderRepository
                    .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(
                            queryMonth, queryDay);
        } else {
            candidates = founderRepository
                    .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrue(
                            queryMonth, queryDay);
        }

        // Also check for leap-day birthdays (Feb 29) being honored on this date
        boolean isFeb28 = businessDate.getMonth() == Month.FEBRUARY && businessDate.getDayOfMonth() == 28;
        boolean isMar1 = businessDate.getMonth() == Month.MARCH && businessDate.getDayOfMonth() == 1;
        boolean isLeapYear = Year.isLeap(businessDate.getYear());

        if (!isLeapYear && isFeb28 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.FEB_28) {
            // Add Feb-29 birthday founders to today's run
            List<Founder> feb29Founders = birthdayConfig.isRequireExplicitConsent()
                    ? founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29)
                    : founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrue(2, 29);
            candidates = mergeLists(candidates, feb29Founders);
        } else if (!isLeapYear && isMar1 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.MARCH_1) {
            List<Founder> feb29Founders = birthdayConfig.isRequireExplicitConsent()
                    ? founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29)
                    : founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrue(2, 29);
            candidates = mergeLists(candidates, feb29Founders);
        }

        // Candidate Individual clients from users collection
        List<User> individualCandidates = new java.util.ArrayList<>();
        if (userRepository != null) {
            List<User> userList = userRepository.findByClientTypeAndBirthdayMonthAndBirthdayDayAndActiveTrue(
                    ClientType.INDIVIDUAL, queryMonth, queryDay);
            for (User u : userList) {
                if (u.isBirthdayGreetingEnabled()) {
                    if (!birthdayConfig.isRequireExplicitConsent() || u.isWhatsappConsentGiven()) {
                        individualCandidates.add(u);
                    }
                }
            }

            if (!isLeapYear && isFeb28 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.FEB_28) {
                List<User> feb29Users = userRepository.findByClientTypeAndBirthdayMonthAndBirthdayDayAndActiveTrue(
                        ClientType.INDIVIDUAL, 2, 29);
                for (User u : feb29Users) {
                    if (u.isBirthdayGreetingEnabled()) {
                        if (!birthdayConfig.isRequireExplicitConsent() || u.isWhatsappConsentGiven()) {
                            if (individualCandidates.stream().noneMatch(m -> m.getUserId().equals(u.getUserId()))) {
                                individualCandidates.add(u);
                            }
                        }
                    }
                }
            } else if (!isLeapYear && isMar1 && birthdayConfig.getLeapDayPolicy() == BirthdayConfig.LeapDayPolicy.MARCH_1) {
                List<User> feb29Users = userRepository.findByClientTypeAndBirthdayMonthAndBirthdayDayAndActiveTrue(
                        ClientType.INDIVIDUAL, 2, 29);
                for (User u : feb29Users) {
                    if (u.isBirthdayGreetingEnabled()) {
                        if (!birthdayConfig.isRequireExplicitConsent() || u.isWhatsappConsentGiven()) {
                            if (individualCandidates.stream().noneMatch(m -> m.getUserId().equals(u.getUserId()))) {
                                individualCandidates.add(u);
                            }
                        }
                    }
                }
            }
        }

        summary.foundersScanned = candidates.size() + individualCandidates.size();
        log.info("[BIRTHDAY-SCHEDULER] Found {} candidate founders and {} individual clients for {}/{}",
                candidates.size(), individualCandidates.size(), queryMonth, queryDay);

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
                recordError("Founder " + founder.getId() + ": " + e.getMessage());
                log.error("[BIRTHDAY-SCHEDULER] Error processing founder '{}': {}",
                        founder.getId(), e.getMessage(), e);
            }
        }

        for (User client : individualCandidates) {
            summary.eligible++;
            try {
                Optional<AnniversaryNotificationLog> result =
                        greetingService.processIndividualBirthday(client, businessDate, false, triggeredBy);
                if (result.isPresent()) {
                    summary.dispatched++;
                } else {
                    summary.skippedOrFailed++;
                }
            } catch (Exception e) {
                summary.skippedOrFailed++;
                recordError("Individual " + client.getUserId() + ": " + e.getMessage());
                log.error("[BIRTHDAY-SCHEDULER] Error processing individual client '{}': {}",
                        client.getUserId(), e.getMessage(), e);
            }
        }

        summary.durationMillis = Duration.between(start, Instant.now()).toMillis();
        this.lastExecutionSummary = summary;
        this.lastExecutionTime = LocalDateTime.now();
        this.lastExecutionStatus = summary.getSkippedOrFailed() > 0 ? "COMPLETED_WITH_SKIPPED_OR_ERRORS" : "COMPLETED_SUCCESSFULLY";
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

    private void recordError(String errorMsg) {
        if (recentErrors.size() >= 20) {
            recentErrors.remove(0);
        }
        recentErrors.add(LocalDateTime.now() + " - " + errorMsg);
    }

    public ExecutionSummary getLastExecutionSummary() {
        return lastExecutionSummary;
    }

    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }

    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }

    public List<String> getRecentErrors() {
        return new ArrayList<>(recentErrors);
    }

    /**
     * Preview founders and individual clients celebrating birthdays on the given business date
     * without sending any WhatsApp messages or creating log records.
     */
    public List<java.util.Map<String, Object>> previewBirthdayGreetings(LocalDate businessDate) {
        int[] monthDay = resolveMatchingMonthDay(businessDate);
        int queryMonth = monthDay[0];
        int queryDay = monthDay[1];

        List<Founder> founders = founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrue(queryMonth, queryDay);
        List<java.util.Map<String, Object>> result = new ArrayList<>();

        for (Founder f : founders) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("recipientType", "FOUNDER");
            item.put("recipientId", f.getId());
            item.put("companyClientId", f.getCompanyClientId());
            item.put("fullName", f.getFullName());
            item.put("whatsappNumber", f.getWhatsappNumber());
            item.put("dateOfBirth", f.getDateOfBirth());
            item.put("birthdayGreetingEnabled", f.isBirthdayGreetingEnabled());
            item.put("whatsappConsentGiven", f.isWhatsappConsentGiven());

            boolean eligible = f.isActive() && f.isBirthdayGreetingEnabled() &&
                    (!birthdayConfig.isRequireExplicitConsent() || f.isWhatsappConsentGiven());
            item.put("eligibleForAutoSend", eligible);
            item.put("eligibilityReason", eligible ? "ELIGIBLE" : "INELIGIBLE_PREFERENCE_OR_CONSENT");
            result.add(item);
        }

        if (userRepository != null) {
            List<User> individuals = userRepository.findByClientTypeAndBirthdayMonthAndBirthdayDayAndActiveTrue(
                    ClientType.INDIVIDUAL, queryMonth, queryDay);
            for (User u : individuals) {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("recipientType", "INDIVIDUAL_CLIENT");
                item.put("recipientId", u.getUserId());
                item.put("companyClientId", "INDIVIDUAL");
                item.put("fullName", u.getFullName());
                item.put("whatsappNumber", u.getPhone());
                item.put("dateOfBirth", u.getDateOfBirth());
                item.put("birthdayGreetingEnabled", u.isBirthdayGreetingEnabled());
                item.put("whatsappConsentGiven", u.isWhatsappConsentGiven());

                boolean eligible = u.isActive() && u.isBirthdayGreetingEnabled() &&
                        (!birthdayConfig.isRequireExplicitConsent() || u.isWhatsappConsentGiven());
                item.put("eligibleForAutoSend", eligible);
                item.put("eligibilityReason", eligible ? "ELIGIBLE" : "INELIGIBLE_PREFERENCE_OR_CONSENT");
                result.add(item);
            }
        }

        return result;
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
