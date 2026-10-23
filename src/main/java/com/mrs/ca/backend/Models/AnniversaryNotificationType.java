package com.mrs.ca.backend.Models;

/**
 * Distinguishes the category of a WhatsApp notification.
 * Each value maps to a unique deduplication key space in the notification log.
 */
public enum AnniversaryNotificationType {
    /** Company formation anniversary (existing). */
    COMPANY_FORMATION_ANNIVERSARY,

    /** Founder personal birthday greeting (new). */
    FOUNDER_BIRTHDAY
}
