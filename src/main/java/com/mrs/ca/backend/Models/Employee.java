package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "employees")
public class Employee {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("employee_id")
    private String employeeId;

    @Field("name")
    private String name;

    @Field("father_name")
    private String fatherName;

    @Field("mobile_number")
    private String mobileNumber;

    @Field("aadhaar_number")
    private String aadhaarNumber;

    @Field("pan_number")
    private String panNumber;

    @Field("date_of_joining")
    private LocalDate dateOfJoining;

    @Field("permanent_address")
    private String permanentAddress;

    @Field("current_address")
    private String currentAddress;

    @Field("email")
    private String email;

    @Field("address_line1")
    private String addressLine1;

    @Field("address_line2")
    private String addressLine2;

    @Field("city")
    private String city;

    @Field("state")
    private String state;

    @Field("pin_code")
    private String pinCode;

    @Field("referred_by")
    private String referredBy;

    @Field("father_mobile_number")
    private String fatherMobileNumber;

    @Field("resume_google_drive_link")
    private String resumeGoogleDriveLink;

    @Field("aadhaar_document_url")
    private String aadhaarDocumentUrl;

    @Field("aadhaar_grid_fs_id")
    private String aadhaarGridFsId;

    @Field("aadhaar_file_name")
    private String aadhaarFileName;

    @Field("aadhaar_file_size")
    private Long aadhaarFileSize;

    @Field("profile_status")
    private ProfileStatus profileStatus = ProfileStatus.INCOMPLETE;

    @Field("form_completed")
    private Boolean formCompleted = false;

    @Field("document_status")
    private DocumentVerificationStatus documentStatus = DocumentVerificationStatus.PENDING;

    @Field("document_rejection_reason")
    private String documentRejectionReason;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Employee() {
    }

    public Employee(String employeeId, String name, String mobileNumber) {
        this.employeeId = employeeId;
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.profileStatus = ProfileStatus.INCOMPLETE;
        this.formCompleted = false;
        this.documentStatus = DocumentVerificationStatus.PENDING;
    }

    // --- Getters and Setters ---

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

    public String getAadhaarGridFsId() {
        return aadhaarGridFsId;
    }

    public void setAadhaarGridFsId(String aadhaarGridFsId) {
        this.aadhaarGridFsId = aadhaarGridFsId;
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
        if (profileStatus == ProfileStatus.SUBMITTED) {
            this.formCompleted = true;
        }
    }

    public Boolean getFormCompleted() {
        return Boolean.TRUE.equals(formCompleted) || profileStatus == ProfileStatus.SUBMITTED;
    }

    public Boolean isFormCompleted() {
        return getFormCompleted();
    }

    public void setFormCompleted(Boolean formCompleted) {
        this.formCompleted = formCompleted;
        if (Boolean.TRUE.equals(formCompleted)) {
            this.profileStatus = ProfileStatus.SUBMITTED;
        }
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
