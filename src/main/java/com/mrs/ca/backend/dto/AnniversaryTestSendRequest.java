package com.mrs.ca.backend.dto;

public class AnniversaryTestSendRequest {

    private String recipientPhone;
    private String companyName = "Test Firm Technologies Pvt. Ltd.";
    private String contactPersonName = "Valued Client";
    private String templateName;

    public AnniversaryTestSendRequest() {}

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
}
