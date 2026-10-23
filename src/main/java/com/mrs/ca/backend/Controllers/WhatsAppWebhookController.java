package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Services.AnniversaryStatusReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller handling Meta WhatsApp Cloud API webhooks.
 * Supports GET for subscription verification and POST for delivery status updates.
 */
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final AnniversaryConfig anniversaryConfig;
    private final AnniversaryStatusReconciliationService reconciliationService;

    public WhatsAppWebhookController(AnniversaryConfig anniversaryConfig,
                                     AnniversaryStatusReconciliationService reconciliationService) {
        this.anniversaryConfig = anniversaryConfig;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Webhook subscription verification endpoint requested by Meta WhatsApp Cloud API.
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        String expectedToken = anniversaryConfig.getWebhookVerifyToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            expectedToken = "mrsco_verify_2026";
        }

        if ("subscribe".equals(mode) && expectedToken.equals(token)) {
            log.info("[WEBHOOK] Meta WhatsApp webhook subscription verified successfully.");
            return ResponseEntity.ok(challenge);
        }

        log.warn("[WEBHOOK] Meta webhook verification failed. Received token: {}", token);
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * Webhook callback receiver for message status updates (sent, delivered, read, failed).
     */
    @PostMapping("/webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> handleWebhookEvent(@RequestBody Map<String, Object> payload) {
        try {
            log.debug("[WEBHOOK] Incoming webhook event received: {}", payload);

            if (payload == null || !payload.containsKey("entry")) {
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries == null) {
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            for (Map<String, Object> entry : entries) {
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes == null) continue;

                for (Map<String, Object> change : changes) {
                    Map<String, Object> value = (Map<String, Object>) change.get("value");
                    if (value == null || !value.containsKey("statuses")) continue;

                    List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
                    if (statuses == null) continue;

                    for (Map<String, Object> statusObj : statuses) {
                        String metaMsgId = (String) statusObj.get("id");
                        String statusStr = (String) statusObj.get("status");
                        Object rawTimestamp = statusObj.get("timestamp");
                        Long timestamp = null;
                        if (rawTimestamp instanceof Number num) {
                            timestamp = num.longValue();
                        } else if (rawTimestamp instanceof String str) {
                            try {
                                timestamp = Long.parseLong(str);
                            } catch (NumberFormatException ignored) {}
                        }

                        List<Map<String, Object>> errors = (List<Map<String, Object>>) statusObj.get("errors");

                        // Reconcile status for anniversary notifications
                        reconciliationService.reconcileStatus(metaMsgId, statusStr, timestamp, errors);
                    }
                }
            }

        } catch (Exception e) {
            log.error("[WEBHOOK] Error processing incoming Meta WhatsApp webhook payload: {}", e.getMessage(), e);
        }

        // Always return 200 OK so Meta does not retry indefinitely
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}