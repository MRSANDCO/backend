package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.EmployeeProfileResponse;
import com.mrs.ca.backend.dto.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/profile")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    private String getAuthenticatedEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new SecurityException("Unauthenticated user");
        }
        return auth.getName();
    }

    /**
     * Employee views their own profile.
     */
    @GetMapping
    public ResponseEntity<?> getMyProfile() {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse profile = employeeService.getProfileForEmployee(employeeId);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Employee updates their profile (only allowed before submission).
     * Returns 403 Forbidden if profileStatus == SUBMITTED.
     */
    @PutMapping
    public ResponseEntity<?> updateMyProfile(@RequestBody UpdateProfileRequest request) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse updated = employeeService.updateProfileByEmployee(employeeId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile updated successfully",
                    "profile", updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Employee submits complete profile. Locks profile from further edits.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitMyProfile() {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse submitted = employeeService.submitProfile(employeeId);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile submitted successfully and locked for review",
                    "profileStatus", submitted.getProfileStatus().name(),
                    "profile", submitted
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Employee uploads Aadhaar / ID proof PDF (only allowed before submission).
     * Returns 403 Forbidden if profileStatus == SUBMITTED.
     */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse profile = employeeService.uploadDocumentByEmployee(employeeId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Document uploaded successfully",
                    "fileName", profile.getAadhaarFileName(),
                    "documentStatus", profile.getDocumentStatus().name()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store document: " + e.getMessage()));
        }
    }

    /**
     * Employee views/downloads their own uploaded document.
     */
    @GetMapping("/document")
    public void downloadMyDocument(HttpServletResponse response) throws IOException {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            employeeService.streamDocument(employeeId, employeeId, false, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }
}
