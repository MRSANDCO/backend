package com.mrs.ca.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.mrs.ca.backend.Models.ClientType;

import java.time.LocalDate;

public class AnniversarySettingsRequest {

    @JsonAlias({"client_type", "clientType"})
    private ClientType clientType;

    private LocalDate formationDate;

    @JsonAlias({"date_of_birth", "dateOfBirth", "dob"})
    private LocalDate dateOfBirth;

    private String companyName;
    private String contactPersonName;
    private Boolean anniversaryAutomationEnabled;

    @JsonAlias({"birthday_greeting_enabled", "birthdayGreetingEnabled"})
    private Boolean birthdayGreetingEnabled;

    private Boolean whatsappConsentGiven;

    public AnniversarySettingsRequest() {}

    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    @JsonSetter("client_type")
    public void setClientTypeSnake(String val) {
        if (val != null && !val.isBlank()) {
            this.clientType = ClientType.fromString(val);
        }
    }

    @JsonSetter("clientType")
    public void setClientTypeCamel(String val) {
        if (val != null && !val.isBlank()) {
            this.clientType = ClientType.fromString(val);
        }
    }

    public LocalDate getFormationDate() { return formationDate; }
    public void setFormationDate(LocalDate formationDate) { this.formationDate = formationDate; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public Boolean getAnniversaryAutomationEnabled() { return anniversaryAutomationEnabled; }
    public void setAnniversaryAutomationEnabled(Boolean anniversaryAutomationEnabled) { this.anniversaryAutomationEnabled = anniversaryAutomationEnabled; }

    public Boolean getBirthdayGreetingEnabled() { return birthdayGreetingEnabled; }
    public void setBirthdayGreetingEnabled(Boolean birthdayGreetingEnabled) { this.birthdayGreetingEnabled = birthdayGreetingEnabled; }

    public Boolean getWhatsappConsentGiven() { return whatsappConsentGiven; }
    public void setWhatsappConsentGiven(Boolean whatsappConsentGiven) { this.whatsappConsentGiven = whatsappConsentGiven; }
}
