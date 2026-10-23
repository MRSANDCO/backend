package com.mrs.ca.backend.dto;

import java.time.LocalDate;

/**
 * Request body for creating or updating a founder record.
 */
public class FounderRequest {

    /** Mandatory on create. Must reference an existing active COMPANY-type client (userId). */
    private String companyClientId;

    /** Founder's full legal name. Required. */
    private String fullName;

    /** Date of birth (ISO date string: YYYY-MM-DD). Required on create. */
    private LocalDate dateOfBirth;

    /**
     * Founder's WhatsApp number with country code (e.g. "919876543210" or "+91 98765 43210").
     * Required on create. The service normalizes to digits-only.
     */
    private String whatsappNumber;

    /** Whether the founder record is active. Defaults to true on create. */
    private Boolean active;

    /** Whether birthday greeting is enabled for this founder. Defaults to false. */
    private Boolean birthdayGreetingEnabled;

    /** Whether the founder has given WhatsApp messaging consent. Defaults to false. */
    private Boolean whatsappConsentGiven;

    public FounderRequest() {
    }

    public String getCompanyClientId() {
        return companyClientId;
    }

    public void setCompanyClientId(String companyClientId) {
        this.companyClientId = companyClientId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getBirthdayGreetingEnabled() {
        return birthdayGreetingEnabled;
    }

    public void setBirthdayGreetingEnabled(Boolean birthdayGreetingEnabled) {
        this.birthdayGreetingEnabled = birthdayGreetingEnabled;
    }

    public Boolean getWhatsappConsentGiven() {
        return whatsappConsentGiven;
    }

    public void setWhatsappConsentGiven(Boolean whatsappConsentGiven) {
        this.whatsappConsentGiven = whatsappConsentGiven;
    }
}
