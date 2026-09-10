package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.Attendance;
import com.mrs.ca.backend.Models.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AttendanceResponse {

    private String attendanceId;
    private String employeeId;
    private String employeeName;
    private LocalDate attendanceDate;
    private String formattedDate;
    private LocalTime checkInTime;
    private String formattedCheckInTime;
    private LocalTime checkOutTime;
    private String formattedCheckOutTime;
    private AttendanceStatus status;
    private String remarks;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    public AttendanceResponse() {
    }

    public static AttendanceResponse fromEntity(Attendance attendance) {
        if (attendance == null) return null;
        AttendanceResponse resp = new AttendanceResponse();
        resp.setAttendanceId(attendance.getAttendanceId());
        resp.setEmployeeId(attendance.getEmployeeId());
        resp.setEmployeeName(attendance.getEmployeeName());
        resp.setAttendanceDate(attendance.getAttendanceDate());
        if (attendance.getAttendanceDate() != null) {
            resp.setFormattedDate(attendance.getAttendanceDate().format(DATE_FORMATTER));
        }
        resp.setCheckInTime(attendance.getCheckInTime());
        if (attendance.getCheckInTime() != null) {
            resp.setFormattedCheckInTime(attendance.getCheckInTime().format(TIME_FORMATTER));
        } else {
            resp.setFormattedCheckInTime("--");
        }
        resp.setCheckOutTime(attendance.getCheckOutTime());
        if (attendance.getCheckOutTime() != null) {
            resp.setFormattedCheckOutTime(attendance.getCheckOutTime().format(TIME_FORMATTER));
        } else {
            resp.setFormattedCheckOutTime("--");
        }
        resp.setStatus(attendance.getStatus());
        resp.setRemarks(attendance.getRemarks());
        return resp;
    }

    // --- Getters and Setters ---

    public String getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(String attendanceId) {
        this.attendanceId = attendanceId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public void setFormattedDate(String formattedDate) {
        this.formattedDate = formattedDate;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public String getFormattedCheckInTime() {
        return formattedCheckInTime;
    }

    public void setFormattedCheckInTime(String formattedCheckInTime) {
        this.formattedCheckInTime = formattedCheckInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getFormattedCheckOutTime() {
        return formattedCheckOutTime;
    }

    public void setFormattedCheckOutTime(String formattedCheckOutTime) {
        this.formattedCheckOutTime = formattedCheckOutTime;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
