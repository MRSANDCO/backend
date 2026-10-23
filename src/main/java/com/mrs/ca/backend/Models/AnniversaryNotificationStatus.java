package com.mrs.ca.backend.Models;

/**
 * Explicit state model for Company Formation Anniversary WhatsApp notifications.
 */
public enum AnniversaryNotificationStatus {
    /**
     * Eligible greeting record created and scheduled for processing.
     */
    PENDING,

    /**
     * Atomically claimed by a worker node/instance with an active lease.
     */
    PROCESSING,

    /**
     * Meta WhatsApp Cloud API accepted the message request and returned a Meta Message ID.
     */
    ACCEPTED,

    /**
     * Sent status callback confirmed by Meta webhook.
     */
    SENT,

    /**
     * Message confirmed delivered to the recipient device by Meta webhook.
     */
    DELIVERED,

    /**
     * Message confirmed read by the recipient by Meta webhook.
     */
    READ,

    /**
     * Message failed permanently or maximum retries exhausted.
     */
    FAILED,

    /**
     * Transient failure occurred; eligible for automatic retry with backoff.
     */
    RETRY_PENDING,

    /**
     * Outcome uncertain (e.g. socket timeout after request dispatched to Meta).
     * Requires reconciliation via webhook or manual review before any resend.
     */
    AMBIGUOUS
}
