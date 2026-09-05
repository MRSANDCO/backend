package com.mrs.ca.backend.dto;

public class DocumentRejectionRequest {

    private String reason;

    public DocumentRejectionRequest() {
    }

    public DocumentRejectionRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
