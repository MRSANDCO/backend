package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.WhatsAppService;
import com.mrs.ca.backend.dto.WhatsAppMessageRequest;
import com.mrs.ca.backend.dto.WhatsAppMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for sending WhatsApp messages using Meta WhatsApp Cloud API.
 */
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);
    private final WhatsAppService whatsAppService;

    public WhatsAppController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    /**
     * Endpoint to send WhatsApp notifications.
     * POST /api/whatsapp/send
     *
     * @param request the target phone and text payload
     * @return 200 OK with Meta Cloud API response body
     */
    @PostMapping("/send")
    public ResponseEntity<WhatsAppMessageResponse> sendWhatsAppMessage(@RequestBody WhatsAppMessageRequest request) {
        log.info("Received request to send WhatsApp notification. Target: {}", request.getTo());
        WhatsAppMessageResponse response = whatsAppService.sendTextMessage(request.getTo(), request.getMessage());
        return ResponseEntity.ok(response);
    }
}
