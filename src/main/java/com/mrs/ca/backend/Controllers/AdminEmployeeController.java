package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.*;
import com.mrs.ca.backend.Models.EmploymentStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/employees")
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    public AdminEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Create employee. Generates Employee ID, User credentials, and initial profile.
     */
    @PostMapping
    public ResponseEntity<?> createEmployee(@RequestBody CreateEmployeeRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminUsername = (auth != null && auth.getName() != null) ? auth.getName() : "admin";

            CreateEmployeeResponse response = employeeService.createEmployee(request, adminUsername);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List and search employees with optional employment-status filter.
     * <p>
     * Query params:
     * <ul>
     *   <li>{@code search} — free-text search (name / employeeId / mobile)</li>
     *   <li>{@code employmentStatus} — {@code ACTIVE} or {@code EX_EMPLOYEE} (omit for all)</li>
     *   <li>{@code page} / {@code size} — pagination</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<?> getAllEmployees(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "employmentStatus", required = false) String employmentStatus,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            String filter = (employmentStatus != null && !employmentStatus.isBlank())
                    ? employmentStatus
                    : status;
            Page<EmployeeProfileResponse> employees;
            if (filter != null && !filter.isBlank()) {
                employees = employeeService.getAllEmployees(search, filter, page, size);
            } else {
                employees = employeeService.getAllEmployees(search, page, size);
            }
            return ResponseEntity.ok(employees);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * View employee profile by ID.
     */
    @GetMapping("/{employeeId}")
    public ResponseEntity<?> getEmployeeById(@PathVariable String employeeId) {
        try {
            EmployeeProfileResponse response = employeeService.getEmployeeByIdForAdmin(employeeId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Edit employee profile (Admin can edit even after submission).
     */
    @PutMapping("/{employeeId}")
    public ResponseEntity<?> updateEmployee(@PathVariable String employeeId,
                                            @RequestBody UpdateProfileRequest request) {
        try {
            EmployeeProfileResponse updated = employeeService.updateEmployeeByAdmin(employeeId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Employee profile updated successfully",
                    "employee", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reset employee password.
     */
    @PostMapping("/{employeeId}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String employeeId,
                                           @RequestBody(required = false) ResetPasswordRequest request) {
        try {
            String newPassword = request != null ? request.getNewPassword() : null;
            String generatedOrSetPassword = employeeService.resetEmployeePassword(employeeId, newPassword);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully",
                    "employeeId", employeeId,
                    "newPassword", generatedOrSetPassword
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Activate / Deactivate employee account.
     * Body: { "active": true | false }
     */
    @PatchMapping("/{employeeId}/status")
    public ResponseEntity<?> setAccountStatus(@PathVariable String employeeId,
                                              @RequestBody Map<String, Object> request) {
        if (request == null || (!request.containsKey("active") && !request.containsKey("status") && !request.containsKey("employmentStatus"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "'active' boolean flag or 'employmentStatus' is required"));
        }
        try {
            EmployeeProfileResponse updated = null;
            if (request.containsKey("employmentStatus") || request.containsKey("status")) {
                Object statusObj = request.containsKey("employmentStatus") ? request.get("employmentStatus") : request.get("status");
                if (statusObj != null && !statusObj.toString().trim().isBlank()) {
                    EmploymentStatus newStatus = EmploymentStatus.valueOf(statusObj.toString().trim().toUpperCase().replace("-", "_"));
                    updated = employeeService.updateEmploymentStatus(employeeId, newStatus);
                }
            }
            if (request.containsKey("active")) {
                boolean active = Boolean.parseBoolean(request.get("active").toString());
                employeeService.setEmployeeActiveStatus(employeeId, active);
                if (!active && updated == null) {
                    updated = employeeService.updateEmploymentStatus(employeeId, EmploymentStatus.EX_EMPLOYEE);
                }
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Employee status updated successfully",
                    "employeeId", employeeId,
                    "employee", updated != null ? updated : employeeService.getEmployeeByIdForAdmin(employeeId)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * View/download employee's uploaded PDF document.
     */
    @GetMapping("/{employeeId}/document")
    public void getEmployeeDocument(@PathVariable String employeeId,
                                    HttpServletResponse response) throws IOException {
        try {
            employeeService.streamDocument(employeeId, null, true, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Verify employee's uploaded document.
     */
    @PostMapping("/{employeeId}/document/verify")
    public ResponseEntity<?> verifyDocument(@PathVariable String employeeId) {
        try {
            EmployeeProfileResponse updated = employeeService.verifyDocument(employeeId);
            return ResponseEntity.ok(Map.of(
                    "message", "Document verified successfully",
                    "employeeId", employeeId,
                    "documentStatus", updated.getDocumentStatus().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update employee employment status (lifecycle change: ACTIVE ↔ EX_EMPLOYEE).
     * <p>
     * This is NOT a delete operation. The employee's complete data remains intact
     * in the database. Only the {@code employmentStatus} field is modified.
     * </p>
     * <p>
     * Request body: {@code { "employmentStatus": "EX_EMPLOYEE" }}
     * </p>
     * <p>
     * Requires ADMIN role (enforced by Spring Security at {@code /api/admin/**}).
     * </p>
     */
    @RequestMapping(
            value = "/{employeeId}/employment-status",
            method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST}
    )
    public ResponseEntity<?> updateEmploymentStatus(
            @PathVariable String employeeId,
            @RequestBody UpdateEmploymentStatusRequest request) {

        if (request == null || request.getEmploymentStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "'employmentStatus' is required. Allowed values: ACTIVE, EX_EMPLOYEE"
            ));
        }

        try {
            EmployeeProfileResponse updated =
                    employeeService.updateEmploymentStatus(employeeId, request.getEmploymentStatus());
            return ResponseEntity.ok(Map.of(
                    "message", "Employment status updated successfully",
                    "employeeId", employeeId,
                    "employmentStatus", updated.getEmploymentStatus().name(),
                    "employee", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permanently delete an employee — removes profile, user account, and GridFS Aadhaar document.
     * ADMIN only — enforced at security config level (no EMPLOYEE role can reach this endpoint).<br>
     * <p>
     * NOTE: Prefer using {@code PATCH /{employeeId}/employment-status} with {@code EX_EMPLOYEE}
     * to preserve historical data when an employee leaves the company.
     * </p>
     */
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<?> deleteEmployee(@PathVariable String employeeId) {
        try {
            employeeService.deleteEmployee(employeeId);
            return ResponseEntity.ok(Map.of(
                    "message", "Employee deleted successfully",
                    "employeeId", employeeId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject employee's uploaded document.
     */
    @PostMapping("/{employeeId}/document/reject")
    public ResponseEntity<?> rejectDocument(@PathVariable String employeeId,
                                            @RequestBody(required = false) DocumentRejectionRequest request) {
        try {
            String reason = request != null ? request.getReason() : null;
            EmployeeProfileResponse updated = employeeService.rejectDocument(employeeId, reason);
            return ResponseEntity.ok(Map.of(
                    "message", "Document rejected",
                    "employeeId", employeeId,
                    "documentStatus", updated.getDocumentStatus().name(),
                    "rejectionReason", updated.getDocumentRejectionReason() != null ? updated.getDocumentRejectionReason() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===================== Dedicated Active / Ex-Employee list endpoints =====================

    /**
     * List all ACTIVE employees (pagination + optional search).
     * Equivalent to GET /employees?employmentStatus=ACTIVE
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActiveEmployees(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            Page<EmployeeProfileResponse> employees =
                    employeeService.getAllEmployees(search, "ACTIVE", page, size);
            return ResponseEntity.ok(employees);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all EX_EMPLOYEE employees (pagination + optional search).
     * Equivalent to GET /employees?employmentStatus=EX_EMPLOYEE
     */
    @GetMapping("/ex-employees")
    public ResponseEntity<?> getExEmployees(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            Page<EmployeeProfileResponse> employees =
                    employeeService.getAllEmployees(search, "EX_EMPLOYEE", page, size);
            return ResponseEntity.ok(employees);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Shorthand: mark an employee as EX_EMPLOYEE (no request body needed).
     * Idempotent — calling this on an already-EX_EMPLOYEE record is safe.
     */
    @RequestMapping(
            value = {"/{employeeId}/mark-ex-employee", "/{employeeId}/mark-as-ex-employee", "/{employeeId}/ex-employee"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH}
    )
    public ResponseEntity<?> markAsExEmployee(@PathVariable String employeeId) {
        try {
            EmployeeProfileResponse updated =
                    employeeService.updateEmploymentStatus(employeeId, EmploymentStatus.EX_EMPLOYEE);
            return ResponseEntity.ok(Map.of(
                    "message", "Employee marked as ex-employee successfully",
                    "employeeId", employeeId,
                    "employmentStatus", updated.getEmploymentStatus().name(),
                    "employee", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Admin verifies employee submitted profile.
     */
    @RequestMapping(
            value = {"/{employeeId}/profile/verify", "/{employeeId}/verify"},
            method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public ResponseEntity<?> verifyProfile(@PathVariable String employeeId) {
        try {
            EmployeeProfileResponse updated = employeeService.verifyProfile(employeeId);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile verified successfully",
                    "employeeId", employeeId,
                    "profileStatus", updated.getProfileStatus().name(),
                    "employee", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Admin rejects employee submitted profile.
     */
    @RequestMapping(
            value = {"/{employeeId}/profile/reject", "/{employeeId}/reject"},
            method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public ResponseEntity<?> rejectProfile(@PathVariable String employeeId,
                                           @RequestBody(required = false) DocumentRejectionRequest request) {
        try {
            String reason = request != null ? request.getReason() : null;
            EmployeeProfileResponse updated = employeeService.rejectProfile(employeeId, reason);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile rejected successfully",
                    "employeeId", employeeId,
                    "profileStatus", updated.getProfileStatus().name(),
                    "rejectionReason", updated.getDocumentRejectionReason() != null ? updated.getDocumentRejectionReason() : "",
                    "employee", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
