package com.mrs.ca.backend.Models;

/**
 * Distinguishes client categories:
 * - COMPANY: Clients with a registered firm or company eligible for company formation anniversary greetings.
 * - INDIVIDUAL: Individual clients receiving personal services without an associated firm; excluded from anniversary greetings.
 */
public enum ClientType {
    COMPANY,
    INDIVIDUAL;

    public static ClientType fromString(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (ClientType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid client type: '" + value + "'. Allowed values: COMPANY, INDIVIDUAL");
    }
}
