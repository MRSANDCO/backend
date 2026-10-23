package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service that reconciles Meta WhatsApp webhook status callbacks (sent, delivered, read, failed).
 * Enforces monotonic state progression to prevent late or duplicate callbacks from downgrading status.
 */
@Service
public class AnniversaryStatusReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AnniversaryStatusReconciliationService.class);

    private final AnniversaryNotificationLogRepository logRepository;
    private final MongoTemplate mongoTemplate;

    public AnniversaryStatusReconciliationService(AnniversaryNotificationLogRepository logRepository,
                                                MongoTemplate mongoTemplate) {
        this.logRepository = logRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Reconciles a single status callback from Meta WhatsApp webhook.
     *
     * @param metaMessageId Meta WhatsApp message ID
     * @param statusString   "sent", "delivered", "read", "failed"
     * @param timestampSec   Epoch seconds timestamp from Meta
     * @param errors         List of error maps if present
     * @return true if an anniversary notification log was matched and updated; false otherwise
     */
    public boolean reconcileStatus(String metaMessageId, String statusString, Long timestampSec, List<Map<String, Object>> errors) {
        if (metaMessageId == null || metaMessageId.isBlank() || statusString == null) {
            return false;
        }

        Optional<AnniversaryNotificationLog> logOpt = logRepository.findByMetaMessageId(metaMessageId);
        if (logOpt.isEmpty()) {
            // Not an anniversary notification or unknown message; ignore gracefully
            return false;
        }

        AnniversaryNotificationLog current = logOpt.get();
        LocalDateTime eventTime = timestampSec != null
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(timestampSec), ZoneId.of("Asia/Kolkata"))
                : LocalDateTime.now();

        log.info("[WEBHOOK-RECONCILE] Processing status '{}' for anniversary log ID '{}', Meta Msg ID '{}'",
                statusString, current.getId(), metaMessageId);

        String normalizedStatus = statusString.toLowerCase().trim();
        Query query = new Query(Criteria.where("_id").is(current.getId()));
        Update update = new Update().set("updated_at", LocalDateTime.now());

        switch (normalizedStatus) {
            case "read" -> {
                // READ is the highest delivery progression
                update.set("status", AnniversaryNotificationStatus.READ);
                update.set("read_at", eventTime);
                if (current.getDeliveryAt() == null) {
                    update.set("delivery_at", eventTime);
                }
            }
            case "delivered" -> {
                // Cannot downgrade if already READ
                if (current.getStatus() == AnniversaryNotificationStatus.READ) {
                    log.info("[WEBHOOK-RECONCILE] Ignoring late 'delivered' event as message is already marked READ.");
                    return true;
                }
                update.set("status", AnniversaryNotificationStatus.DELIVERED);
                update.set("delivery_at", eventTime);
            }
            case "sent" -> {
                // Cannot downgrade if already DELIVERED or READ
                if (current.getStatus() == AnniversaryNotificationStatus.READ ||
                        current.getStatus() == AnniversaryNotificationStatus.DELIVERED) {
                    log.info("[WEBHOOK-RECONCILE] Ignoring late 'sent' event as message is already DELIVERED/READ.");
                    return true;
                }
                update.set("status", AnniversaryNotificationStatus.SENT);
            }
            case "failed" -> {
                // If message was already delivered or read, a failed callback is contradictory or out-of-order
                if (current.getStatus() == AnniversaryNotificationStatus.READ ||
                        current.getStatus() == AnniversaryNotificationStatus.DELIVERED) {
                    log.warn("[WEBHOOK-RECONCILE] Received 'failed' callback after message was already confirmed delivered.");
                    return true;
                }
                update.set("status", AnniversaryNotificationStatus.FAILED);
                if (errors != null && !errors.isEmpty()) {
                    Map<String, Object> err = errors.get(0);
                    update.set("last_error_code", String.valueOf(err.getOrDefault("code", "WEBHOOK_FAILED")));
                    update.set("last_error_message", String.valueOf(err.getOrDefault("title", "Delivery failed via webhook")));
                } else {
                    update.set("last_error_code", "WEBHOOK_FAILED");
                    update.set("last_error_message", "Meta webhook reported delivery failure");
                }
            }
            default -> {
                log.info("[WEBHOOK-RECONCILE] Unhandled status event '{}' for message ID {}", normalizedStatus, metaMessageId);
                return true;
            }
        }

        mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), AnniversaryNotificationLog.class);
        log.info("[WEBHOOK-RECONCILE] Successfully updated anniversary notification log ID '{}' to '{}'",
                current.getId(), normalizedStatus);
        return true;
    }
}
