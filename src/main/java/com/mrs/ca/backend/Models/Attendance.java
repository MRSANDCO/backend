package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Document(collection = "attendances")
@CompoundIndex(name = "emp_date_unique_idx", def = "{'employee_id': 1, 'attendance_date': 1}", unique = true)
public class Attendance {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("attendance_id")
    private String attendanceId;

    @Indexed
    @Field("employee_id")
    private String employeeId;

    @Field("employee_name")
    private String employeeName;

    @Indexed
    @Field("attendance_date")
    private LocalDate attendanceDate;

    @Field("check_in_time")
    private LocalTime checkInTime;

    @Field("check_out_time")
    private LocalTime checkOutTime;

    @Field("status")
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Field("remarks")
    private String remarks;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Attendance() {
    }

    public Attendance(String attendanceId, String employeeId, String employeeName, LocalDate attendanceDate, LocalTime checkInTime, AttendanceStatus status) {
        this.attendanceId = attendanceId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.attendanceDate = attendanceDate;
        this.checkInTime = checkInTime;
        this.status = status != null ? status : AttendanceStatus.PRESENT;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
