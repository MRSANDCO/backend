package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("user_id")
    private String userId;

    @JsonIgnore
    @Field("password")
    private String password;

    @Field("full_name")
    private String fullName;

    @Field("email")
    private String email;

    @Field("phone")
    private String phone;

    @Field("active")
    private boolean active = true;

    @Field("role")
    private String role = "USER";

    @JsonIgnore
    @Field("directory_path")
    private String directoryPath;

    @Field("created_by_admin")
    private String createdByAdmin;

    @DBRef
    private List<DocumentAssignment> documentAssignments = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("client_type")
    private ClientType clientType;

    @Field("company_name")
    private String companyName;

    @Field("contact_person_name")
    private String contactPersonName;

    @Field("formation_date")
    private java.time.LocalDate formationDate;

    @Field("anniversary_automation_enabled")
    private boolean anniversaryAutomationEnabled = false;

    @Field("whatsapp_consent_given")
    private boolean whatsappConsentGiven = false;

    @Field("whatsapp_consent_date")
    private LocalDateTime whatsappConsentDate;

    @Field("date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Field("birthday_month")
    private Integer birthdayMonth;

    @Field("birthday_day")
    private Integer birthdayDay;

    @Field("birthday_greeting_enabled")
    private Boolean birthdayGreetingEnabled = true;

    public User() {
    }

    public User(String userId, String password, String fullName, String email, String createdByAdmin) {
        this.userId = userId;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.createdByAdmin = createdByAdmin;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getRole() {
        return role != null ? role : "USER";
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public String getCreatedByAdmin() {
        return createdByAdmin;
    }

    public void setCreatedByAdmin(String createdByAdmin) {
        this.createdByAdmin = createdByAdmin;
    }

    public List<DocumentAssignment> getDocumentAssignments() {
        return documentAssignments;
    }

    public void setDocumentAssignments(List<DocumentAssignment> documentAssignments) {
        this.documentAssignments = documentAssignments;
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

    public java.time.LocalDate getFormationDate() {
        return formationDate;
    }

    public void setFormationDate(java.time.LocalDate formationDate) {
        this.formationDate = formationDate;
    }

    public boolean isAnniversaryAutomationEnabled() {
        return anniversaryAutomationEnabled;
    }

    public void setAnniversaryAutomationEnabled(boolean anniversaryAutomationEnabled) {
        this.anniversaryAutomationEnabled = anniversaryAutomationEnabled;
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

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public java.time.LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(java.time.LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        if (dateOfBirth != null) {
            this.birthdayMonth = dateOfBirth.getMonthValue();
            this.birthdayDay = dateOfBirth.getDayOfMonth();
        } else {
            this.birthdayMonth = null;
            this.birthdayDay = null;
        }
    }

    public Integer getBirthdayMonth() {
        return birthdayMonth;
    }

    public void setBirthdayMonth(Integer birthdayMonth) {
        this.birthdayMonth = birthdayMonth;
    }

    public Integer getBirthdayDay() {
        return birthdayDay;
    }

    public void setBirthdayDay(Integer birthdayDay) {
        this.birthdayDay = birthdayDay;
    }

    public boolean isBirthdayGreetingEnabled() {
        return birthdayGreetingEnabled == null || birthdayGreetingEnabled;
    }

    public void setBirthdayGreetingEnabled(Boolean birthdayGreetingEnabled) {
        this.birthdayGreetingEnabled = birthdayGreetingEnabled != null ? birthdayGreetingEnabled : true;
    }

    public String getEffectiveCompanyName() {
        if (companyName != null && !companyName.isBlank()) {
            return companyName.trim();
        }
        return fullName != null ? fullName.trim() : "";
    }

    public String getEffectiveContactPersonName() {
        if (contactPersonName != null && !contactPersonName.isBlank()) {
            return contactPersonName.trim();
        }
        return fullName != null ? fullName.trim() : "";
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", clientType=" + clientType +
                ", formationDate=" + formationDate +
                ", dateOfBirth=" + dateOfBirth +
                ", email='" + email + '\'' +
                ", active=" + active +
                '}';
    }
}
