package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.FounderRepository;
import com.mrs.ca.backend.dto.WhatsAppSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core orchestration service for founder birthday greetings.
 *
 * <h3>Deduplication strategy</h3>
 * <p>Birthday notifications reuse the {@code anniversary_notification_logs} collection.
 * The deduplication key is {@code (clientId=founderId, anniversaryYear=currentYear,
 * notificationType=FOUNDER_BIRTHDAY)}. This is disjoint from the company anniversary key
 * which uses {@code COMPANY_FORMATION_ANNIVERSARY}, so there is no conflict.</p>
 *
 * <h3>Concurrency safety</h3>
 * <p>A {@code findAndModify} atomic claim with a 5-minute lease prevents duplicate sends
 * across concurrent scheduler instances. DuplicateKeyException handling covers the race
 * between log insertion and the unique index enforcement.</p>
 *
 * <h3>Leap-day policy</h3>
 * <p>Founders born on Feb 29 are greeted on Feb 28 in non-leap years (configurable to Mar 1).
 * This mirrors the existing {@link AnniversaryMatchingService} leap-day logic.</p>
 */
@Service
public class BirthdayGreetingService {

    private static final Logger log = LoggerFactory.getLogger(BirthdayGreetingService.class);

    private final BirthdayConfig birthdayConfig;
    private final WhatsAppService whatsAppService;
    private final AnniversaryNotificationLogRepository logRepository;
    private final FounderRepository founderRepository;
    private final AnniversaryEligibilityService phoneValidator;
    private final MongoTemplate mongoTemplate;

    /**
     * Unique identifier for this JVM instance; used in the processing-worker-id lease field
     * so concurrent workers can detect stale leases and reclaim records safely.
     */
    private final String workerId = UUID.randomUUID().toString();

    public BirthdayGreetingService(BirthdayConfig birthdayConfig,
                                   WhatsAppService whatsAppService,
                                   AnniversaryNotificationLogRepository logRepository,
                                   FounderRepository founderRepository,
                                   AnniversaryEligibilityService phoneValidator,
                                   MongoTemplate mongoTemplate) {
        this.birthdayConfig = birthdayConfig;
        this.whatsAppService = whatsAppService;
        this.logRepository = logRepository;
        this.founderRepository = founderRepository;
        this.phoneValidator = phoneValidator;
        this.mongoTemplate = mongoTemplate;
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Process a birthday greeting for a single eligible founder.
     *
     * @param founder      the founder record (must be active, greetingEnabled, consentGiven)
     * @param businessDate the date being processed
     * @param manual       {@code true} when triggered by an admin, {@code false} for scheduled runs
     * @param triggeredBy  actor string for audit log ("SCHEDULER", "ADMIN_TRIGGER", etc.)
     * @return the persisted notification log if a dispatch was attempted, empty otherwise
     */
    public Optional<AnniversaryNotificationLog> processFounderBirthday(
            Founder founder, LocalDate businessDate, boolean manual, String triggeredBy) {

        if (founder == null || businessDate == null) {
            return Optional.empty();
        }

        int birthdayYear = businessDate.getYear();
        String founderId = founder.getId();

        // --- 1. Quick eligibility re-check (scheduler already filtered, but guard in service too) ---
        if (!founder.isActive() || !founder.isBirthdayGreetingEnabled() || !founder.isWhatsappConsentGiven()) {
            log.info("[BIRTHDAY-SERVICE] Skipping founder '{}' — active={}, greetingEnabled={}, consent={}",
                    founderId, founder.isActive(), founder.isBirthdayGreetingEnabled(), founder.isWhatsappConsentGiven());
            return Optional.empty();
        }

        String normalizedPhone = phoneValidator.normalizeAndValidatePhone(founder.getWhatsappNumber());
        if (normalizedPhone == null) {
            log.warn("[BIRTHDAY-SERVICE] Skipping founder '{}' — phone '{}' is not a valid international number.",
                    founderId, founder.getWhatsappNumber());
            return Optional.empty();
        }

        // --- 2. Create-or-fetch notification log (database-level idempotency) ---
        AnniversaryNotificationLog logRecord = getOrCreateBirthdayLog(
                founder, businessDate, normalizedPhone, manual, triggeredBy);
        if (logRecord == null) {
            log.info("[BIRTHDAY-SERVICE] Birthday log already exists/accepted for founder '{}' year {}.",
                    founderId, birthdayYear);
            return Optional.empty();
        }

        // --- 3. Atomic lease claim ---
        AnniversaryNotificationLog claimedLog = claimForProcessing(logRecord.getId());
        if (claimedLog == null) {
            log.info("[BIRTHDAY-SERVICE] Concurrent worker claimed founder '{}' year {}. Skipping.",
                    founderId, birthdayYear);
            return Optional.empty();
        }

        // --- 4. Dispatch template to Meta WhatsApp Cloud API ---
        String templateName = birthdayConfig.getTemplateName();
        String templateLang = birthdayConfig.getTemplateLanguage();
        String founderName = resolveFounderName(founder);

        // Template variable mapping:
        // {{1}} = Founder's full name
        List<String> bodyParams = List.of(founderName);

        log.info("[BIRTHDAY-SERVICE] Dispatching birthday template '{}' ({}) to founder '{}' at '{}'",
                templateName, templateLang, founderName, normalizedPhone);

        WhatsAppSendResult sendResult = whatsAppService.sendTemplateMessage(
                normalizedPhone, templateName, templateLang, bodyParams);

        // --- 5. Persist outcome ---
        AnniversaryNotificationLog finalLog = updateOutcome(claimedLog.getId(), sendResult, birthdayYear);
        return Optional.ofNullable(finalLog);
    }

    /**
     * Retry any birthday notifications in RETRY_PENDING status whose backoff window has elapsed.
     *
     * @return number of records successfully retried
     */
    public int retryPendingBirthdayGreetings() {
        LocalDateTime now = LocalDateTime.now();
        List<AnniversaryNotificationLog> retryList = logRepository
                .findByStatusAndNextRetryAtLessThanEqual(AnniversaryNotificationStatus.RETRY_PENDING, now)
                .stream()
                .filter(r -> r.getNotificationType() == AnniversaryNotificationType.FOUNDER_BIRTHDAY)
                .toList();

        if (retryList.isEmpty()) {
            return 0;
        }

        log.info("[BIRTHDAY-RETRY] Found {} pending birthday notifications to retry.", retryList.size());
        int processed = 0;

        for (AnniversaryNotificationLog item : retryList) {
            try {
                Optional<Founder> founderOpt = founderRepository.findById(item.getClientId());
                if (founderOpt.isEmpty()) {
                    markPermanentFailure(item.getId(), "FOUNDER_DELETED", "Founder record no longer exists");
                    continue;
                }

                Founder founder = founderOpt.get();
                if (!founder.isActive() || !founder.isBirthdayGreetingEnabled() || !founder.isWhatsappConsentGiven()) {
                    markPermanentFailure(item.getId(), "INELIGIBLE_ON_RETRY",
                            "Founder inactive, greeting disabled, or consent withdrawn before retry");
                    continue;
                }

                String normalizedPhone = phoneValidator.normalizeAndValidatePhone(founder.getWhatsappNumber());
                if (normalizedPhone == null) {
                    markPermanentFailure(item.getId(), "INVALID_PHONE", "Phone number no longer valid for retry");
                    continue;
                }

                AnniversaryNotificationLog claimed = claimForProcessing(item.getId());
                if (claimed == null) continue;

                WhatsAppSendResult result = whatsAppService.sendTemplateMessage(
                        normalizedPhone,
                        item.getTemplateName(),
                        item.getTemplateLanguage(),
                        List.of(resolveFounderName(founder))
                );

                updateOutcome(claimed.getId(), result, item.getAnniversaryYear());
                processed++;

            } catch (Exception e) {
                log.error("[BIRTHDAY-RETRY] Error retrying birthday notification ID {}: {}", item.getId(), e.getMessage(), e);
            }
        }

        return processed;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Creates or fetches the birthday notification log for the (founderId, year) pair.
     * The compound unique index on (clientId, anniversaryYear, notificationType) prevents duplicates.
     */
    private AnniversaryNotificationLog getOrCreateBirthdayLog(
            Founder founder, LocalDate businessDate, String normalizedPhone,
            boolean manual, String triggeredBy) {

        int birthdayYear = businessDate.getYear();
        String founderId = founder.getId();

        Optional<AnniversaryNotificationLog> existing = logRepository
                .findByClientIdAndAnniversaryYearAndNotificationType(
                        founderId, birthdayYear, AnniversaryNotificationType.FOUNDER_BIRTHDAY);

        if (existing.isPresent()) {
            AnniversaryNotificationLog current = existing.get();
            // Block re-send if already successfully dispatched or outcome is unknown
            if (current.getStatus() == AnniversaryNotificationStatus.ACCEPTED ||
                    current.getStatus() == AnniversaryNotificationStatus.SENT ||
                    current.getStatus() == AnniversaryNotificationStatus.DELIVERED ||
                    current.getStatus() == AnniversaryNotificationStatus.READ ||
                    current.getStatus() == AnniversaryNotificationStatus.AMBIGUOUS) {
                return null;
            }
            return current;
        }

        AnniversaryNotificationLog newLog = new AnniversaryNotificationLog(
                founderId,                        // clientId = founder doc ID
                founder.getCompanyClientId(),     // userDocId = company client ID for traceability
                founder.getFullName(),             // companyName field reused for founder name
                founder.getFullName(),             // contactPersonName
                normalizedPhone,
                birthdayYear,
                businessDate
        );
        newLog.setNotificationType(AnniversaryNotificationType.FOUNDER_BIRTHDAY);
        newLog.setTemplateName(birthdayConfig.getTemplateName());
        newLog.setTemplateLanguage(birthdayConfig.getTemplateLanguage());
        newLog.setMaxAttempts(birthdayConfig.getMaxRetryAttempts());
        newLog.setManualTrigger(manual);
        newLog.setTriggeredBy(triggeredBy != null ? triggeredBy : "SCHEDULER");
        newLog.setCreatedAt(LocalDateTime.now());
        newLog.setUpdatedAt(LocalDateTime.now());

        try {
            return logRepository.save(newLog);
        } catch (DuplicateKeyException e) {
            log.warn("[BIRTHDAY-SERVICE] DuplicateKeyException for founder '{}' year {}. Concurrent instance beat us.",
                    founderId, birthdayYear);
            return null;
        }
    }

    /**
     * Atomically claims a birthday notification log for processing with a 5-minute lease.
     * Expired leases are recoverable by any worker instance.
     */
    private AnniversaryNotificationLog claimForProcessing(String logId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.plusMinutes(5);

        Query query = new Query(
                Criteria.where("_id").is(logId)
                        .orOperator(
                                Criteria.where("status").in(
                                        AnniversaryNotificationStatus.PENDING,
                                        AnniversaryNotificationStatus.RETRY_PENDING),
                                Criteria.where("status").is(AnniversaryNotificationStatus.PROCESSING)
                                        .and("lease_expires_at").lt(now)
                        )
        );

        Update update = new Update()
                .set("status", AnniversaryNotificationStatus.PROCESSING)
                .set("processing_worker_id", workerId)
                .set("lease_expires_at", leaseExpiry)
                .set("last_attempt_at", now)
                .set("updated_at", now)
                .inc("attempt_count", 1);

        return mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                AnniversaryNotificationLog.class);
    }

    /**
     * Updates the notification log record based on the WhatsApp API send result.
     */
    private AnniversaryNotificationLog updateOutcome(String logId, WhatsAppSendResult result, int birthdayYear) {
        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria.where("_id").is(logId));
        Update update = new Update()
                .set("updated_at", now)
                .unset("lease_expires_at")
                .unset("processing_worker_id");

        if (result.isAccepted()) {
            log.info("[BIRTHDAY-SERVICE] Message accepted by Meta. Meta Message ID: {}", result.getMetaMessageId());
            update.set("status", AnniversaryNotificationStatus.ACCEPTED);
            update.set("meta_message_id", result.getMetaMessageId());
            update.set("api_acceptance_at", now);
            update.unset("last_error_code");
            update.unset("last_error_message");
            update.unset("next_retry_at");

        } else if (result.isAmbiguous()) {
            log.warn("[BIRTHDAY-SERVICE] Ambiguous outcome for log '{}'. Code: {}", logId, result.getErrorCode());
            update.set("status", AnniversaryNotificationStatus.AMBIGUOUS);
            update.set("last_error_code", result.getErrorCode());
            update.set("last_error_message", result.getErrorMessage());
            update.unset("next_retry_at");

        } else {
            log.error("[BIRTHDAY-SERVICE] Send failed for log '{}'. Code: {}, Retryable: {}",
                    logId, result.getErrorCode(), result.isRetryable());
            update.set("last_error_code", result.getErrorCode());
            update.set("last_error_message", result.getErrorMessage());

            AnniversaryNotificationLog current = logRepository.findById(logId).orElse(null);
            int currentAttempt = current != null ? current.getAttemptCount() : 1;
            int maxAttempts = current != null ? current.getMaxAttempts() : birthdayConfig.getMaxRetryAttempts();

            if (result.isRetryable() && currentAttempt < maxAttempts) {
                long delayMinutes = (long) (birthdayConfig.getRetryInitialIntervalMinutes() *
                        Math.pow(birthdayConfig.getRetryMultiplier(), currentAttempt - 1));
                LocalDateTime nextRetry = now.plusMinutes(Math.max(1, delayMinutes));
                update.set("status", AnniversaryNotificationStatus.RETRY_PENDING);
                update.set("next_retry_at", nextRetry);
                log.info("[BIRTHDAY-SERVICE] Retry #{} scheduled for log '{}' at {}", currentAttempt + 1, logId, nextRetry);
            } else {
                update.set("status", AnniversaryNotificationStatus.FAILED);
                update.unset("next_retry_at");
                log.warn("[BIRTHDAY-SERVICE] Permanently FAILED for log '{}'", logId);
            }
        }

        return mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                AnniversaryNotificationLog.class);
    }

    private void markPermanentFailure(String logId, String errorCode, String errorMessage) {
        log.warn("[BIRTHDAY-RETRY] Marking birthday notification '{}' as FAILED: {}", logId, errorMessage);
        Query query = new Query(Criteria.where("_id").is(logId));
        Update update = new Update()
                .set("status", AnniversaryNotificationStatus.FAILED)
                .set("last_error_code", errorCode)
                .set("last_error_message", errorMessage)
                .set("updated_at", LocalDateTime.now())
                .unset("lease_expires_at")
                .unset("processing_worker_id")
                .unset("next_retry_at");
        mongoTemplate.findAndModify(query, update, AnniversaryNotificationLog.class);
    }

    private String resolveFounderName(Founder founder) {
        return (founder.getFullName() != null && !founder.getFullName().isBlank())
                ? founder.getFullName().trim()
                : "Valued Founder";
    }
}
