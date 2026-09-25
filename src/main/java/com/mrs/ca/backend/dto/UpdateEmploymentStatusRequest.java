package com.mrs.ca.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.mrs.ca.backend.Models.EmploymentStatus;

/**
 * Request body for the Admin-only PATCH /employees/{employeeId}/employment-status endpoint.
 * <p>
 * Only {@code employmentStatus} is accepted. No other employee field is read or modified
 * by this endpoint, ensuring existing profile data remains completely intact.
 * </p>
 *
 * Example:
 * <pre>
 * {
 *   "employmentStatus": "EX_EMPLOYEE"
 * }
 * </pre>
 */
public class UpdateEmploymentStatusRequest {

    @JsonAlias({"status", "employment_status", "employmentStatus"})
    private EmploymentStatus employmentStatus;

    public UpdateEmploymentStatusRequest() {
    }

    public UpdateEmploymentStatusRequest(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    @JsonSetter
    public void setEmploymentStatus(String statusStr) {
        if (statusStr != null && !statusStr.trim().isBlank()) {
            String normalized = statusStr.trim().toUpperCase().replace("-", "_");
            this.employmentStatus = EmploymentStatus.valueOf(normalized);
        }
    }
}
