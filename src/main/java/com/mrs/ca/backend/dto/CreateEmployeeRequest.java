package com.mrs.ca.backend.dto;

public class CreateEmployeeRequest {

    private String name;
    private String mobileNumber;
    private String initialPassword;

    public CreateEmployeeRequest() {
    }

    public CreateEmployeeRequest(String name, String mobileNumber) {
        this.name = name;
        this.mobileNumber = mobileNumber;
    }

    public CreateEmployeeRequest(String name, String mobileNumber, String initialPassword) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.initialPassword = initialPassword;
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
}
