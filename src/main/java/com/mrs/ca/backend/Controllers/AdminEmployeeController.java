package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.*;
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
     * List and search employees.
     */
    @GetMapping
    public ResponseEntity<Page<EmployeeProfileResponse>> getAllEmployees(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<EmployeeProfileResponse> employees = employeeService.getAllEmployees(search, page, size);
        return ResponseEntity.ok(employees);
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
                                              @RequestBody Map<String, Boolean> request) {
        if (!request.containsKey("active")) {
            return ResponseEntity.badRequest().body(Map.of("error", "'active' boolean flag is required"));
        }
        try {
            boolean active = Boolean.TRUE.equals(request.get("active"));
            employeeService.setEmployeeActiveStatus(employeeId, active);
            return ResponseEntity.ok(Map.of(
                    "message", "Employee account active status updated to " + active,
                    "employeeId", employeeId,
                    "active", active
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
     * Permanently delete an employee — removes profile, user account, and GridFS Aadhaar document.
     * ADMIN only — enforced at security config level (no EMPLOYEE role can reach this endpoint).
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
}
