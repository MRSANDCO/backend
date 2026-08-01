package com.mrs.ca.backend.dto;

/**
 * DTO representing the incoming request to send a WhatsApp message.
 */
public class WhatsAppMessageRequest {
    private String to;
    private String message;

    public WhatsAppMessageRequest() {}

    public WhatsAppMessageRequest(String to, String message) {
        this.to = to;
        this.message = message;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
