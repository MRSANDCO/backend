package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.WhatsAppConfig;
import com.mrs.ca.backend.dto.WhatsAppMessageResponse;
import com.mrs.ca.backend.exception.WhatsAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
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
}