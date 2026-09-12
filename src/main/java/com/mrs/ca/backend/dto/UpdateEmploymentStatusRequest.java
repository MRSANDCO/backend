package com.mrs.ca.backend.dto;

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

    private EmploymentStatus employmentStatus;

    public UpdateEmploymentStatusRequest() {
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }
}
