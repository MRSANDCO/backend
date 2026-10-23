package com.mrs.ca.backend.dto;

/**
 * Result representation of a Meta WhatsApp Cloud API template dispatch.
 */
public class WhatsAppSendResult {

    private final boolean accepted;
    private final String metaMessageId;
    private final int httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final boolean ambiguous;
    private final boolean retryable;

    private WhatsAppSendResult(boolean accepted, String metaMessageId, int httpStatus,
                               String errorCode, String errorMessage, boolean ambiguous, boolean retryable) {
        this.accepted = accepted;
        this.metaMessageId = metaMessageId;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.ambiguous = ambiguous;
        this.retryable = retryable;
    }

    public static WhatsAppSendResult success(String metaMessageId, int httpStatus) {
        return new WhatsAppSendResult(true, metaMessageId, httpStatus, null, null, false, false);
    }

    public static WhatsAppSendResult failure(int httpStatus, String errorCode, String errorMessage, boolean retryable) {
        return new WhatsAppSendResult(false, null, httpStatus, errorCode, errorMessage, false, retryable);
    }

    public static WhatsAppSendResult ambiguous(int httpStatus, String errorCode, String errorMessage) {
        return new WhatsAppSendResult(false, null, httpStatus, errorCode, errorMessage, true, false);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMetaMessageId() {
        return metaMessageId;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isAmbiguous() {
        return ambiguous;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
