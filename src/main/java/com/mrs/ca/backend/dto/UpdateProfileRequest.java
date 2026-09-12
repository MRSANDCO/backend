package com.mrs.ca.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class UpdateProfileRequest {

    @JsonAlias({"name", "full_name", "fullName"})
    private String name;

    @JsonAlias({"father_name", "fatherName"})
    private String fatherName;

    @JsonAlias({"mobile_number", "mobileNumber", "phone", "mobile"})
    private String mobileNumber;

    @JsonAlias({"aadhaar_number", "aadhaarNumber", "aadhaar"})
    private String aadhaarNumber;

    @JsonAlias({"pan_number", "panNumber", "pan"})
    private String panNumber;

    @JsonAlias({"date_of_joining", "dateOfJoining", "joining_date", "joiningDate"})
    private LocalDate dateOfJoining;

    @JsonAlias({"permanent_address", "permanentAddress"})
    private String permanentAddress;

    @JsonAlias({"current_address", "currentAddress"})
    private String currentAddress;

    @JsonAlias({"father_mobile_number", "fatherMobileNumber", "father_mobile"})
    private String fatherMobileNumber;

    @JsonAlias({"resume_google_drive_link", "resumeGoogleDriveLink", "resume_link", "resumeLink"})
    private String resumeGoogleDriveLink;

    public UpdateProfileRequest() {
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

    @JsonSetter("date_of_joining")
    public void setDateOfJoiningString(String dateStr) {
        if (dateStr != null && !dateStr.trim().isBlank()) {
            this.dateOfJoining = parseDateLeniently(dateStr.trim());
        }
    }

    @JsonSetter("dateOfJoining")
    public void setDateOfJoiningStringCamel(String dateStr) {
        if (dateStr != null && !dateStr.trim().isBlank()) {
            this.dateOfJoining = parseDateLeniently(dateStr.trim());
        }
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

    private static LocalDate parseDateLeniently(String text) {
        if (text.contains("T")) {
            text = text.split("T")[0];
        }
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format: '" + text + "'. Expected format e.g. YYYY-MM-DD or DD-MM-YYYY");
        }
    }
}
