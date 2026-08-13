package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.WhatsAppConfig;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.dto.WhatsAppMessageResponse;
import com.mrs.ca.backend.exception.WhatsAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service implementation for interacting with the Meta WhatsApp Cloud API.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final WhatsAppConfig whatsAppConfig;
    private final RestTemplate restTemplate;

    public WhatsAppService(WhatsAppConfig whatsAppConfig, RestTemplate restTemplate) {
        this.whatsAppConfig = whatsAppConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a text message using the Meta WhatsApp Cloud API.
     *
     * @param to      recipient's phone number with country code (e.g. "919876543210")
     * @param message body of the message to send
     * @return the Meta API response DTO if successful
     */
    public WhatsAppMessageResponse sendTextMessage(String to, String message) {
        if (to == null || to.isBlank()) {
            throw new WhatsAppException("Recipient phone number ('to') is required");
        }
        if (message == null || message.isBlank()) {
            throw new WhatsAppException("Message content is required");
        }

        // Construct API URL
        String url = String.format("%s/%s/%s/messages",
                whatsAppConfig.getBaseUrl(),
                whatsAppConfig.getApiVersion(),
                whatsAppConfig.getPhoneNumberId()
        );

        log.info("Sending WhatsApp message to {} using Meta API at {}", to, url);

        // Build Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(whatsAppConfig.getAccessToken());

        // Build Payload according to Meta API schema
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("preview_url", false);
        textObject.put("body", message);
        payload.put("text", textObject);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            log.info("Outgoing request payload to Meta API: {}", payload);
            ResponseEntity<WhatsAppMessageResponse> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    WhatsAppMessageResponse.class
            );

            log.info("Received Meta API Response. Status: {}", response.getStatusCode());
            WhatsAppMessageResponse body = response.getBody();
            if (body != null && body.getMessages() != null && !body.getMessages().isEmpty()) {
                log.info("WhatsApp message dispatched successfully. Meta Message ID: {}", body.getMessages().get(0).getId());
            }
            return body;

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorResponse = e.getResponseBodyAsString();
            log.error("Meta API error. HTTP Code: {}, Response Body: {}", e.getStatusCode(), errorResponse, e);
            throw new WhatsAppException("Failed to send WhatsApp message. Meta API returned: " + errorResponse, e);
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending WhatsApp message", e);
            throw new WhatsAppException("Failed to send WhatsApp message due to an unexpected error", e);
        }
    }

    /**
     * Send a WhatsApp notification to the client asynchronously.
     * Gracefully catches and logs all errors so it never interrupts the query creation flow.
     */
    @Async
    public void sendQueryNotification(User targetUser, Query query) {
        String clientPhone = targetUser.getPhone();
        String clientId = targetUser.getUserId();

        if (clientPhone == null || clientPhone.isBlank()) {
            log.warn("[WHATSAPP] Skipping notification for client ID '{}' — no phone number on record.", clientId);
            return;
        }

        String normalizedPhone = normalizePhoneNumber(clientPhone);
        if (normalizedPhone.isEmpty()) {
            log.warn("[WHATSAPP] Skipping notification for client ID '{}' — phone number '{}' has no digits.", clientId, clientPhone);
            return;
        }

        String accessToken = whatsAppConfig.getAccessToken();
        String phoneNumberId = whatsAppConfig.getPhoneNumberId();

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("[WHATSAPP] WhatsApp access token is not configured. Skipping notification.");
            return;
        }

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("[WHATSAPP] WhatsApp phone number ID is not configured. Skipping notification.");
            return;
        }

        try {
            log.info("[WHATSAPP] WhatsApp notification attempted for client ID: {}, normalized phone: {}",
                    clientId, normalizedPhone);

            String clientName = targetUser.getFullName() != null && !targetUser.getFullName().isBlank()
                    ? targetUser.getFullName()
                    : clientId;

            // Prepare template JSON payload structure
            // NOTE: The template 'query_raised_notification' currently has 0 body parameters.
            // If you update the template to include {{1}} for the client name, re-add the components block below.
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizedPhone,
                    "type", "template",
                    "template", Map.of(
                            "name", "query_raised_notification",
                            "language", Map.of("code", "en")
                    )
            );

            /* ---- UNCOMMENT THIS BLOCK once your Meta template includes {{1}} for the client name ----
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizedPhone,
                    "type", "template",
                    "template", Map.of(
                            "name", "query_raised_notification",
                            "language", Map.of("code", "en"),
                            "components", List.of(
                                    Map.of(
                                            "type", "body",
                                            "parameters", List.of(
                                                    Map.of("type", "text", "text", clientName)
                                            )
                                    )
                            )
                    )
            );
            ---- END OF COMMENTED BLOCK ---- */

            // Construct API URL using Config
            String url = String.format("%s/%s/%s/messages",
                    whatsAppConfig.getBaseUrl().replaceAll("/$", ""),
                    whatsAppConfig.getApiVersion(),
                    phoneNumberId
            );

            log.info("[WHATSAPP] Sending template message to URL: {}, payload: {}", url, payload);

            // Build Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("[WHATSAPP] WhatsApp notification successfully sent to client ID: {}. Status: {}, Body: {}",
                    clientId, response.getStatusCode(), response.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[WHATSAPP] Meta API error for client ID: {}. HTTP Status: {}, Response Body: {}",
                    clientId, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[WHATSAPP] Unexpected error for client ID: {}. Message: {}",
                    clientId, e.getMessage(), e);
        }
    }

    /**
     * Normalize the phone number to digits only (e.g. country code + number, no +/spaces/dashes).
     */
    public String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    /**
     * Test WhatsApp configuration by sending a plain text message (no template required).
     * Use this from the admin test endpoint to verify credentials and phone number ID are correct.
     *
     * @param to recipient phone number with country code (e.g. "919876543210")
     * @return diagnostic result string
     */
    public String testWhatsAppConfiguration(String to) {
        String accessToken = whatsAppConfig.getAccessToken();
        String phoneNumberId = whatsAppConfig.getPhoneNumberId();
        String apiVersion = whatsAppConfig.getApiVersion();
        String baseUrl = whatsAppConfig.getBaseUrl();

        if (accessToken == null || accessToken.isBlank()) {
            return "FAILURE: WHATSAPP_ACCESS_TOKEN is not configured.";
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return "FAILURE: WHATSAPP_PHONE_NUMBER_ID is not configured.";
        }

        String normalizedTo = normalizePhoneNumber(to);
        if (normalizedTo.isEmpty()) {
            return "FAILURE: Invalid recipient phone number provided.";
        }

        String url = String.format("%s/%s/%s/messages",
                baseUrl.replaceAll("/$", ""), apiVersion, phoneNumberId);

        log.info("[WHATSAPP-TEST] Testing WhatsApp API. URL={}, to={}, phoneNumberId={}, apiVersion={}",
                url, normalizedTo, phoneNumberId, apiVersion);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizedTo);
        payload.put("type", "text");
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("preview_url", false);
        textObj.put("body", "✅ MRS & Co. WhatsApp integration test message. If you see this, the API is working correctly.");
        payload.put("text", textObj);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("[WHATSAPP-TEST] Success. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            return "SUCCESS: WhatsApp test message sent to " + normalizedTo + ". Response: " + response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[WHATSAPP-TEST] Meta API error. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "FAILURE: Meta API returned HTTP " + e.getStatusCode() + " — " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("[WHATSAPP-TEST] Unexpected error", e);
            return "FAILURE: " + e.getMessage();
        }
    }
}