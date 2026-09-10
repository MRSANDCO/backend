package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.AttendanceStatus;

import java.time.LocalTime;

public class AdminAttendanceUpdateRequest {

    private AttendanceStatus status;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private String remarks;

    public AdminAttendanceUpdateRequest() {
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
