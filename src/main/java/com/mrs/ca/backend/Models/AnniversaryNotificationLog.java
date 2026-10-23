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
 * Dedicated persistent notification history record for company formation anniversary greetings.
 * Enforces database-level idempotency via a compound unique index on (clientId, anniversaryYear, notificationType).
 */
@Document(collection = "anniversary_notification_logs")
@CompoundIndexes({
        @CompoundIndex(name = "client_year_type_unique_idx", def = "{'client_id': 1, 'anniversary_year': 1, 'notification_type': 1}", unique = true),
        @CompoundIndex(name = "status_retry_idx", def = "{'status': 1, 'next_retry_at': 1}"),
        @CompoundIndex(name = "anniversary_date_status_idx", def = "{'anniversary_date': 1, 'status': 1}")
})
public class AnniversaryNotificationLog {

    @Id
    private String id;

    @Indexed
    @Field("client_id")
    private String clientId;

    @Field("user_doc_id")
    private String userDocId;

    @Field("company_name")
    private String companyName;

    @Field("contact_person_name")
    private String contactPersonName;

    @Field("recipient_phone")
    private String recipientPhone;

    @Field("anniversary_year")
    private int anniversaryYear;

    @Field("anniversary_date")
    private LocalDate anniversaryDate;

    @Field("years_completed")
    private Integer yearsCompleted;

    @Field("notification_type")
    private AnniversaryNotificationType notificationType = AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY;

    @Field("status")
    private AnniversaryNotificationStatus status = AnniversaryNotificationStatus.PENDING;

    @Indexed(sparse = true)
    @Field("meta_message_id")
    private String metaMessageId;

    @Field("template_name")
    private String templateName;

    @Field("template_language")
    private String templateLanguage;

    @Field("attempt_count")
    private int attemptCount = 0;

    @Field("max_attempts")
    private int maxAttempts = 3;

    @Field("last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Field("api_acceptance_at")
    private LocalDateTime apiAcceptanceAt;

    @Field("delivery_at")
    private LocalDateTime deliveryAt;

    @Field("read_at")
    private LocalDateTime readAt;

    @Field("last_error_code")
    private String lastErrorCode;

    @Field("last_error_message")
    private String lastErrorMessage;

    @Field("next_retry_at")
    private LocalDateTime nextRetryAt;

    @Field("processing_worker_id")
    private String processingWorkerId;

    @Field("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Field("manual_trigger")
    private boolean manualTrigger = false;

    @Field("triggered_by")
    private String triggeredBy = "SCHEDULER";

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public AnniversaryNotificationLog() {
    }

    public AnniversaryNotificationLog(String clientId, String userDocId, String companyName,
                                      String contactPersonName, String recipientPhone,
                                      int anniversaryYear, LocalDate anniversaryDate) {
        this.clientId = clientId;
        this.userDocId = userDocId;
        this.companyName = companyName;
        this.contactPersonName = contactPersonName;
        this.recipientPhone = recipientPhone;
        this.anniversaryYear = anniversaryYear;
        this.anniversaryDate = anniversaryDate;
        this.notificationType = AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY;
        this.status = AnniversaryNotificationStatus.PENDING;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserDocId() {
        return userDocId;
    }

    public void setUserDocId(String userDocId) {
        this.userDocId = userDocId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactPersonName() {
        return contactPersonName;
    }

    public void setContactPersonName(String contactPersonName) {
        this.contactPersonName = contactPersonName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public int getAnniversaryYear() {
        return anniversaryYear;
    }

    public void setAnniversaryYear(int anniversaryYear) {
        this.anniversaryYear = anniversaryYear;
    }

    public LocalDate getAnniversaryDate() {
        return anniversaryDate;
    }

    public void setAnniversaryDate(LocalDate anniversaryDate) {
        this.anniversaryDate = anniversaryDate;
    }

    public Integer getYearsCompleted() {
        return yearsCompleted;
    }

    public void setYearsCompleted(Integer yearsCompleted) {
        this.yearsCompleted = yearsCompleted;
    }

    public AnniversaryNotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(AnniversaryNotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public AnniversaryNotificationStatus getStatus() {
        return status;
    }

    public void setStatus(AnniversaryNotificationStatus status) {
        this.status = status;
    }

    public String getMetaMessageId() {
        return metaMessageId;
    }

    public void setMetaMessageId(String metaMessageId) {
        this.metaMessageId = metaMessageId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateLanguage() {
        return templateLanguage;
    }

    public void setTemplateLanguage(String templateLanguage) {
        this.templateLanguage = templateLanguage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public LocalDateTime getApiAcceptanceAt() {
        return apiAcceptanceAt;
    }

    public void setApiAcceptanceAt(LocalDateTime apiAcceptanceAt) {
        this.apiAcceptanceAt = apiAcceptanceAt;
    }

    public LocalDateTime getDeliveryAt() {
        return deliveryAt;
    }

    public void setDeliveryAt(LocalDateTime deliveryAt) {
        this.deliveryAt = deliveryAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getProcessingWorkerId() {
        return processingWorkerId;
    }

    public void setProcessingWorkerId(String processingWorkerId) {
        this.processingWorkerId = processingWorkerId;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(LocalDateTime leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public boolean isManualTrigger() {
        return manualTrigger;
    }

    public void setManualTrigger(boolean manualTrigger) {
        this.manualTrigger = manualTrigger;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
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
}
