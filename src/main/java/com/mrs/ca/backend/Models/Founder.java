package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a founder of a company associated with an existing COMPANY-type client record.
 * <p>
 * A company client may have multiple founders. Each founder owns a separate WhatsApp number
 * and a distinct birthday greeting preference. No login credentials are created for founders;
 * they are managed solely by the admin.
 * </p>
 *
 * <p><strong>Deduplication:</strong> the birthday scheduler uses the compound index on
 * {@code (companyClientId, founderId, birthdayYear, notificationType)} inside
 * {@code AnniversaryNotificationLog} to enforce exactly-once delivery per calendar year.</p>
 */
@Document(collection = "founders")
@CompoundIndexes({
        @CompoundIndex(
                name = "company_founder_unique_idx",
                def = "{'company_client_id': 1, 'whatsapp_number': 1}",
                unique = true,
                sparse = false
        ),
        @CompoundIndex(
                name = "birthday_month_day_active_idx",
                def = "{'birthday_month': 1, 'birthday_day': 1, 'active': 1, 'birthday_greeting_enabled': 1}"
        )
})
public class Founder {

    @Id
    private String id;

    /**
     * The {@link User#getUserId()} of the linked COMPANY client.
     * Must reference an existing active company record.
     */
    @Indexed
    @Field("company_client_id")
    private String companyClientId;

    @Field("full_name")
    private String fullName;

    /**
     * Stored as ISO date (date-only). The scheduler matches on month + day only.
     * Leap-day (Feb 29) birthdays follow the same policy as anniversary leap-day handling.
     */
    @Field("date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Pre-computed month ordinal (1–12) derived from dateOfBirth to enable efficient index scans.
     * Set automatically by the service on save; do not set directly.
     */
    @Field("birthday_month")
    private int birthdayMonth;

    /**
     * Pre-computed day-of-month (1–31) derived from dateOfBirth.
     * Set automatically by the service on save; do not set directly.
     */
    @Field("birthday_day")
    private int birthdayDay;

    /**
     * Founder's own WhatsApp number with country code (e.g. "919876543210").
     * The scheduler sends the birthday greeting to this number — never to the company's number.
     */
    @Field("whatsapp_number")
    private String whatsappNumber;

    /** Whether this founder record is active and should be considered by the scheduler. */
    @Field("active")
    private boolean active = true;

    /**
     * Whether the birthday greeting is enabled for this founder.
     * Must be {@code true} for the scheduler to process this record.
     */
    @Field("birthday_greeting_enabled")
    private boolean birthdayGreetingEnabled = false;

    /**
     * Whether the founder has given explicit WhatsApp messaging consent.
     * Required before the scheduler will dispatch a greeting.
     * Matches the same compliance model used by {@link User#isWhatsappConsentGiven()}.
     */
    @Field("whatsapp_consent_given")
    private boolean whatsappConsentGiven = false;

    @Field("whatsapp_consent_date")
    private LocalDateTime whatsappConsentDate;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Founder() {
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        if (dateOfBirth != null) {
            this.birthdayMonth = dateOfBirth.getMonthValue();
            this.birthdayDay = dateOfBirth.getDayOfMonth();
        }
    }

    public int getBirthdayMonth() {
        return birthdayMonth;
    }

    public int getBirthdayDay() {
        return birthdayDay;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isBirthdayGreetingEnabled() {
        return birthdayGreetingEnabled;
    }

    public void setBirthdayGreetingEnabled(boolean birthdayGreetingEnabled) {
        this.birthdayGreetingEnabled = birthdayGreetingEnabled;
    }

    public boolean isWhatsappConsentGiven() {
        return whatsappConsentGiven;
    }

    public void setWhatsappConsentGiven(boolean whatsappConsentGiven) {
        this.whatsappConsentGiven = whatsappConsentGiven;
    }

    public LocalDateTime getWhatsappConsentDate() {
        return whatsappConsentDate;
    }

    public void setWhatsappConsentDate(LocalDateTime whatsappConsentDate) {
        this.whatsappConsentDate = whatsappConsentDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Founder{" +
                "id='" + id + '\'' +
                ", companyClientId='" + companyClientId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", dateOfBirth=" + dateOfBirth +
                ", active=" + active +
                ", birthdayGreetingEnabled=" + birthdayGreetingEnabled +
                '}';
    }
}
