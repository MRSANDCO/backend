package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.ProfileStatus;

public class CreateEmployeeResponse {

    private String employeeId;
    private String name;
    private String mobileNumber;
    private String initialPassword;
    private ProfileStatus profileStatus;
    private String message;

    public CreateEmployeeResponse() {
    }

    public CreateEmployeeResponse(String employeeId, String name, String mobileNumber,
                                  String initialPassword, ProfileStatus profileStatus, String message) {
        this.employeeId = employeeId;
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.initialPassword = initialPassword;
        this.profileStatus = profileStatus;
        this.message = message;
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

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    public ProfileStatus getProfileStatus() {
        return profileStatus;
    }

    public void setProfileStatus(ProfileStatus profileStatus) {
        this.profileStatus = profileStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
