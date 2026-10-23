package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.ClientType;

import java.time.LocalDate;

/**
 * Data transfer object representing an eligible company client for admin dropdowns,
 * such as the Add Founder modal in the admin portal.
 */
public class CompanyDropdownDto {

    private String companyId;
    private String userId;
    private String companyName;
    private String contactPersonName;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate formationDate;
    private boolean active;
    private ClientType clientType;
    private boolean anniversaryAutomationEnabled;
    private boolean whatsappConsentGiven;
    private long founderCount;

    public CompanyDropdownDto() {}

    public CompanyDropdownDto(String userId, String companyName, String contactPersonName,
                              String fullName, String phone, String email, LocalDate formationDate,
                              boolean active, ClientType clientType, boolean anniversaryAutomationEnabled,
                              boolean whatsappConsentGiven, long founderCount) {
        this.companyId = userId;
        this.userId = userId;
        this.companyName = companyName;
        this.contactPersonName = contactPersonName;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.formationDate = formationDate;
        this.active = active;
        this.clientType = clientType;
        this.anniversaryAutomationEnabled = anniversaryAutomationEnabled;
        this.whatsappConsentGiven = whatsappConsentGiven;
        this.founderCount = founderCount;
    }

    public String getCompanyId() {
        return companyId != null ? companyId : userId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
        if (this.userId == null) {
            this.userId = companyId;
        }
    }

    public String getUserId() {
        return userId != null ? userId : companyId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        if (this.companyId == null) {
            this.companyId = userId;
        }
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getFormationDate() {
        return formationDate;
    }

    public void setFormationDate(LocalDate formationDate) {
        this.formationDate = formationDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
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

    public long getFounderCount() {
        return founderCount;
    }

    public void setFounderCount(long founderCount) {
        this.founderCount = founderCount;
    }
}
