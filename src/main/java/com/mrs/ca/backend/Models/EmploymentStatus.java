package com.mrs.ca.backend.Models;

/**
 * Represents the employment lifecycle status of an employee.
 * <p>
 * ACTIVE      – currently employed; included in all active-employee queries.
 * EX_EMPLOYEE – no longer employed; data is fully preserved in the database
 *               for historical/audit reference. Never deleted.
 * </p>
 */
public enum EmploymentStatus {
    ACTIVE,
    EX_EMPLOYEE
}
