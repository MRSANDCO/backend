package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.DocumentVerificationStatus;
import com.mrs.ca.backend.Models.ProfileStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeProfileResponse {

    private String id;
    private String employeeId;
    private String name;
    private String fatherName;
    private String mobileNumber;
    private String aadhaarNumber;
    private String panNumber;
    private LocalDate dateOfJoining;
    private String permanentAddress;
    private String currentAddress;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pinCode;
    private String referredBy;
    private String fatherMobileNumber;
    private String resumeGoogleDriveLink;
    private String aadhaarDocumentUrl;
    private String aadhaarFileName;
    private Long aadhaarFileSize;
    private ProfileStatus profileStatus;
    private Boolean formCompleted;
    private DocumentVerificationStatus documentStatus;
    private String documentRejectionReason;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EmployeeProfileResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress) {
        this.currentAddress = currentAddress;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public String getFatherMobileNumber() {
        return fatherMobileNumber;
    }

    public void setFatherMobileNumber(String fatherMobileNumber) {
        this.fatherMobileNumber = fatherMobileNumber;
    }

    public String getResumeGoogleDriveLink() {
        return resumeGoogleDriveLink;
    }

    public void setResumeGoogleDriveLink(String resumeGoogleDriveLink) {
        this.resumeGoogleDriveLink = resumeGoogleDriveLink;
    }

    public String getAadhaarDocumentUrl() {
        return aadhaarDocumentUrl;
    }

    public void setAadhaarDocumentUrl(String aadhaarDocumentUrl) {
        this.aadhaarDocumentUrl = aadhaarDocumentUrl;
    }

    public String getAadhaarFileName() {
        return aadhaarFileName;
    }

    public void setAadhaarFileName(String aadhaarFileName) {
        this.aadhaarFileName = aadhaarFileName;
    }

    public Long getAadhaarFileSize() {
        return aadhaarFileSize;
    }

    public void setAadhaarFileSize(Long aadhaarFileSize) {
        this.aadhaarFileSize = aadhaarFileSize;
    }

    public ProfileStatus getProfileStatus() {
        return profileStatus;
    }

    public void setProfileStatus(ProfileStatus profileStatus) {
        this.profileStatus = profileStatus;
    }

    public Boolean getFormCompleted() {
        return Boolean.TRUE.equals(formCompleted) || profileStatus == ProfileStatus.SUBMITTED;
    }

    public Boolean isFormCompleted() {
        return getFormCompleted();
    }

    public void setFormCompleted(Boolean formCompleted) {
        this.formCompleted = formCompleted;
    }

    public DocumentVerificationStatus getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(DocumentVerificationStatus documentStatus) {
        this.documentStatus = documentStatus;
    }

    public String getDocumentRejectionReason() {
        return documentRejectionReason;
    }

    public void setDocumentRejectionReason(String documentRejectionReason) {
        this.documentRejectionReason = documentRejectionReason;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
