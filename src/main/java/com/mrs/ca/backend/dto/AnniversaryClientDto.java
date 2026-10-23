package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.ClientType;

import java.time.LocalDate;

public class AnniversaryClientDto {

    private String userId;
    private String fullName;
    private ClientType clientType;
    private String companyName;
    private String contactPersonName;
    private String phone;
    private LocalDate formationDate;
    private boolean anniversaryAutomationEnabled;
    private boolean whatsappConsentGiven;
    private int yearsCompleted;
    private boolean anniversaryToday;
    private String latestNotificationStatus;

    public AnniversaryClientDto() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getFormationDate() { return formationDate; }
    public void setFormationDate(LocalDate formationDate) { this.formationDate = formationDate; }

    public boolean isAnniversaryAutomationEnabled() { return anniversaryAutomationEnabled; }
    public void setAnniversaryAutomationEnabled(boolean anniversaryAutomationEnabled) { this.anniversaryAutomationEnabled = anniversaryAutomationEnabled; }

    public boolean isWhatsappConsentGiven() { return whatsappConsentGiven; }
    public void setWhatsappConsentGiven(boolean whatsappConsentGiven) { this.whatsappConsentGiven = whatsappConsentGiven; }

    public int getYearsCompleted() { return yearsCompleted; }
    public void setYearsCompleted(int yearsCompleted) { this.yearsCompleted = yearsCompleted; }

    public boolean isAnniversaryToday() { return anniversaryToday; }
    public void setAnniversaryToday(boolean anniversaryToday) { this.anniversaryToday = anniversaryToday; }

    public String getLatestNotificationStatus() { return latestNotificationStatus; }
    public void setLatestNotificationStatus(String latestNotificationStatus) { this.latestNotificationStatus = latestNotificationStatus; }
}
