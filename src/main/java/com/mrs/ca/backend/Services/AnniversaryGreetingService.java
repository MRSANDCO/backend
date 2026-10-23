package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
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
 * Core orchestration service for company formation anniversary greetings.
 * Provides atomic claiming, duplicate prevention, Meta template dispatch, and retry backoff.
 */
@Service
public class AnniversaryGreetingService {

    private static final Logger log = LoggerFactory.getLogger(AnniversaryGreetingService.class);

    private final AnniversaryConfig anniversaryConfig;
    private final AnniversaryEligibilityService eligibilityService;
    private final WhatsAppService whatsAppService;
    private final AnniversaryNotificationLogRepository logRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final String workerId = UUID.randomUUID().toString();

    public AnniversaryGreetingService(AnniversaryConfig anniversaryConfig,
                                      AnniversaryEligibilityService eligibilityService,
                                      WhatsAppService whatsAppService,
                                      AnniversaryNotificationLogRepository logRepository,
                                      UserRepository userRepository,
                                      MongoTemplate mongoTemplate) {
        this.anniversaryConfig = anniversaryConfig;
        this.eligibilityService = eligibilityService;
        this.whatsAppService = whatsAppService;
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Process an anniversary greeting for a single client on the target business date.
     */
    public Optional<AnniversaryNotificationLog> processClientGreeting(User client, LocalDate businessDate,
                                                                      boolean manual, String triggeredBy) {
        if (client == null || businessDate == null) {
            return Optional.empty();
        }

        int anniversaryYear = businessDate.getYear();
        String clientId = client.getUserId();

        // 1. Check eligibility
        AnniversaryEligibilityService.EligibilityResult eligibility =
                eligibilityService.evaluateEligibility(client, businessDate);

        if (!eligibility.isEligible()) {
            log.info("[ANNIVERSARY-SERVICE] Skipping client '{}' for year {}. Reason: {}",
                    clientId, anniversaryYear, eligibility.getReason());
            return Optional.empty();
        }

        // 2. Database-enforced creation / claim of notification log record
        AnniversaryNotificationLog logRecord = getOrCreateLog(client, businessDate, manual, triggeredBy);
        if (logRecord == null) {
            log.info("[ANNIVERSARY-SERVICE] Notification log already exists or is claimed for client '{}' in year {}.",
                    clientId, anniversaryYear);
            return Optional.empty();
        }

        // 3. Atomically claim lease on this record
        AnniversaryNotificationLog claimedLog = claimForProcessing(logRecord.getId());
        if (claimedLog == null) {
            log.info("[ANNIVERSARY-SERVICE] Concurrent worker claimed client '{}' for year {}. Skipping duplicate.",
                    clientId, anniversaryYear);
            return Optional.empty();
        }

        // 4. Dispatch template via Meta WhatsApp Cloud API (external call performed without open DB locks)
        if (anniversaryConfig.getContactFallbackPolicy() == AnniversaryConfig.ContactFallbackPolicy.SKIP) {
            String rawContact = client.getContactPersonName();
            String rawFull = client.getFullName();
            if ((rawContact == null || rawContact.isBlank()) && (rawFull == null || rawFull.isBlank())) {
                log.warn("[ANNIVERSARY-SERVICE] Skipping client '{}' for year {} because contact name is unavailable and fallback policy is SKIP",
                        clientId, anniversaryYear);
                return Optional.empty();
            }
        }

        String contactPerson = resolveContactPersonName(client);
        String companyName = resolveCompanyName(client);
        String templateName = anniversaryConfig.getTemplateName();
        String templateLang = anniversaryConfig.getTemplateLanguage();
        String recipientPhone = eligibility.getNormalizedPhone();

        // Exact variable mapping:
        // {{1}} = Client contact person's name
        // {{2}} = Company or firm name
        List<String> bodyParams = List.of(contactPerson, companyName);

        log.info("[ANNIVERSARY-SERVICE] Dispatching anniversary greeting template '{}' ({}) to '{}' ({}) at phone '{}'",
                templateName, templateLang, contactPerson, companyName, recipientPhone);

        WhatsAppSendResult sendResult = whatsAppService.sendTemplateMessage(
                recipientPhone, templateName, templateLang, bodyParams);

        // 5. Persist the outcome with exact state transition
        AnniversaryNotificationLog finalLog = updateOutcome(claimedLog.getId(), sendResult);
        return Optional.ofNullable(finalLog);
    }

    /**
     * Resolves the contact person name for template variable {{1}}, honoring fallback configuration.
     */
    public String resolveContactPersonName(User client) {
        if (client != null) {
            if (client.getContactPersonName() != null && !client.getContactPersonName().isBlank()) {
                return client.getContactPersonName().trim();
            }
            if (client.getFullName() != null && !client.getFullName().isBlank()) {
                return client.getFullName().trim();
            }
        }
        return anniversaryConfig.getFallbackContactName();
    }

    /**
     * Resolves the company or firm name for template variable {{2}}, honoring fallback configuration.
     */
    public String resolveCompanyName(User client) {
        if (client != null) {
            if (client.getCompanyName() != null && !client.getCompanyName().isBlank()) {
                return client.getCompanyName().trim();
            }
            if (client.getFullName() != null && !client.getFullName().isBlank()) {
                return client.getFullName().trim();
            }
        }
        return anniversaryConfig.getFallbackCompanyName();
    }

    /**
     * Creates or fetches the notification log. Protected by compound unique index.
     */
    private AnniversaryNotificationLog getOrCreateLog(User client, LocalDate businessDate,
                                                      boolean manual, String triggeredBy) {
        int anniversaryYear = businessDate.getYear();
        String clientId = client.getUserId();

        Optional<AnniversaryNotificationLog> existing = logRepository
                .findByClientIdAndAnniversaryYearAndNotificationType(
                        clientId, anniversaryYear, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY);

        if (existing.isPresent()) {
            AnniversaryNotificationLog current = existing.get();
            // If already accepted, sent, delivered, or read, duplicate send is blocked
            if (current.getStatus() == AnniversaryNotificationStatus.ACCEPTED ||
                    current.getStatus() == AnniversaryNotificationStatus.SENT ||
                    current.getStatus() == AnniversaryNotificationStatus.DELIVERED ||
                    current.getStatus() == AnniversaryNotificationStatus.READ ||
                    current.getStatus() == AnniversaryNotificationStatus.AMBIGUOUS) {
                return null;
            }
            return current;
        }

        String resolvedCompany = resolveCompanyName(client);
        String resolvedContact = resolveContactPersonName(client);

        AnniversaryNotificationLog newLog = new AnniversaryNotificationLog(
                clientId,
                client.getId(),
                resolvedCompany,
                resolvedContact,
                client.getPhone(),
                anniversaryYear,
                businessDate
        );
        newLog.setTemplateName(anniversaryConfig.getTemplateName());
        newLog.setTemplateLanguage(anniversaryConfig.getTemplateLanguage());
        newLog.setMaxAttempts(anniversaryConfig.getMaxRetryAttempts());
        newLog.setManualTrigger(manual);
        newLog.setTriggeredBy(triggeredBy != null ? triggeredBy : "SCHEDULER");
        newLog.setCreatedAt(LocalDateTime.now());
        newLog.setUpdatedAt(LocalDateTime.now());

        try {
            return logRepository.save(newLog);
        } catch (DuplicateKeyException e) {
            log.warn("[ANNIVERSARY-SERVICE] DuplicateKeyException caught for client '{}' in year {}. Record created by concurrent instance.",
                    clientId, anniversaryYear);
            return null;
        }
    }

    /**
     * Atomically claims an anniversary notification log for processing with a 5-minute lease.
     */
    private AnniversaryNotificationLog claimForProcessing(String logId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.plusMinutes(5);

        Query query = new Query(Criteria.where("_id").is(logId)
                .orOperator(
                        Criteria.where("status").in(AnniversaryNotificationStatus.PENDING, AnniversaryNotificationStatus.RETRY_PENDING),
                        Criteria.where("status").is(AnniversaryNotificationStatus.PROCESSING)
                                .and("lease_expires_at").lt(now) // Expired lease recovery
                ));

        Update update = new Update()
                .set("status", AnniversaryNotificationStatus.PROCESSING)
                .set("processing_worker_id", workerId)
                .set("lease_expires_at", leaseExpiry)
                .set("last_attempt_at", now)
                .set("updated_at", now)
                .inc("attempt_count", 1);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        return mongoTemplate.findAndModify(query, update, options, AnniversaryNotificationLog.class);
    }

    /**
     * Updates notification log record based on WhatsApp API response.
     */
    private AnniversaryNotificationLog updateOutcome(String logId, WhatsAppSendResult result) {
        LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria.where("_id").is(logId));
        Update update = new Update().set("updated_at", now);

        // Always release lease
        update.unset("lease_expires_at");
        update.unset("processing_worker_id");

        if (result.isAccepted()) {
            log.info("[ANNIVERSARY-SERVICE] Message accepted by Meta. Meta Message ID: {}", result.getMetaMessageId());
            update.set("status", AnniversaryNotificationStatus.ACCEPTED);
            update.set("meta_message_id", result.getMetaMessageId());
            update.set("api_acceptance_at", now);
            update.unset("last_error_code");
            update.unset("last_error_message");
            update.unset("next_retry_at");
        } else if (result.isAmbiguous()) {
            log.warn("[ANNIVERSARY-SERVICE] Message outcome is ambiguous for log ID {}. Code: {}, Msg: {}",
                    logId, result.getErrorCode(), result.getErrorMessage());
            update.set("status", AnniversaryNotificationStatus.AMBIGUOUS);
            update.set("last_error_code", result.getErrorCode());
            update.set("last_error_message", result.getErrorMessage());
            update.unset("next_retry_at");
        } else {
            // Failure case
            log.error("[ANNIVERSARY-SERVICE] Message failed for log ID {}. Code: {}, Msg: {}, Retryable: {}",
                    logId, result.getErrorCode(), result.getErrorMessage(), result.isRetryable());
            update.set("last_error_code", result.getErrorCode());
            update.set("last_error_message", result.getErrorMessage());

            // Check if retry is appropriate
            AnniversaryNotificationLog current = logRepository.findById(logId).orElse(null);
            int currentAttempt = current != null ? current.getAttemptCount() : 1;
            int maxAttempts = current != null ? current.getMaxAttempts() : anniversaryConfig.getMaxRetryAttempts();

            if (result.isRetryable() && currentAttempt < maxAttempts) {
                // Exponential backoff
                long delayMinutes = (long) (anniversaryConfig.getRetryInitialIntervalMinutes() *
                        Math.pow(anniversaryConfig.getRetryMultiplier(), currentAttempt - 1));
                LocalDateTime nextRetry = now.plusMinutes(Math.max(1, delayMinutes));
                update.set("status", AnniversaryNotificationStatus.RETRY_PENDING);
                update.set("next_retry_at", nextRetry);
                log.info("[ANNIVERSARY-SERVICE] Scheduled retry #{} for log ID {} at {}", currentAttempt + 1, logId, nextRetry);
            } else {
                update.set("status", AnniversaryNotificationStatus.FAILED);
                update.unset("next_retry_at");
                log.warn("[ANNIVERSARY-SERVICE] Message marked as permanently FAILED for log ID {}", logId);
            }
        }

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        return mongoTemplate.findAndModify(query, update, options, AnniversaryNotificationLog.class);
    }

    /**
     * Executes retries for any notifications in RETRY_PENDING status whose nextRetryAt has arrived.
     */
    public int retryPendingGreetings() {
        LocalDateTime now = LocalDateTime.now();
        List<AnniversaryNotificationLog> retryList = logRepository
                .findByStatusAndNextRetryAtLessThanEqual(AnniversaryNotificationStatus.RETRY_PENDING, now);

        if (retryList.isEmpty()) {
            return 0;
        }

        log.info("[ANNIVERSARY-RETRY] Found {} pending anniversary notifications to retry.", retryList.size());
        int processedCount = 0;

        for (AnniversaryNotificationLog item : retryList) {
            try {
                // 1. Re-verify client and opt-in status before retry
                Optional<User> userOpt = userRepository.findByUserId(item.getClientId());
                if (userOpt.isEmpty()) {
                    markPermanentFailure(item.getId(), "CLIENT_DELETED", "Client no longer exists");
                    continue;
                }

                User client = userOpt.get();
                if (!client.isActive() || client.getClientType() != ClientType.COMPANY ||
                        !client.isAnniversaryAutomationEnabled() || !client.isWhatsappConsentGiven()) {
                    markPermanentFailure(item.getId(), "CONSENT_WITHDRAWN_OR_INACTIVE_OR_NOT_COMPANY",
                            "Client opted out, inactive, not a company, or automation disabled before retry");
                    continue;
                }

                // 2. Re-claim lease atomically
                AnniversaryNotificationLog claimed = claimForProcessing(item.getId());
                if (claimed == null) {
                    continue;
                }

                // 3. Dispatch template to Meta
                String normalizedPhone = eligibilityService.normalizeAndValidatePhone(client.getPhone());
                if (normalizedPhone == null) {
                    markPermanentFailure(claimed.getId(), "INVALID_PHONE", "Phone number is no longer valid");
                    continue;
                }

                String contactPerson = resolveContactPersonName(client);
                String companyName = resolveCompanyName(client);

                WhatsAppSendResult result = whatsAppService.sendTemplateMessage(
                        normalizedPhone,
                        item.getTemplateName(),
                        item.getTemplateLanguage(),
                        List.of(contactPerson, companyName)
                );

                updateOutcome(claimed.getId(), result);
                processedCount++;

            } catch (Exception e) {
                log.error("[ANNIVERSARY-RETRY] Error retrying notification ID {}: {}", item.getId(), e.getMessage(), e);
            }
        }

        return processedCount;
    }

    private void markPermanentFailure(String logId, String errorCode, String errorMessage) {
        log.warn("[ANNIVERSARY-RETRY] Marking notification ID {} as permanently failed: {}", logId, errorMessage);
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
}
