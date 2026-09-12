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
     * Employee submits complete profile with final form payload. Locks profile from further edits.
     * Document must be uploaded prior to final submission via POST /api/employee/profile/document.
     */
    @PostMapping(value = "/submit", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> submitMyProfile(@RequestBody(required = false) UpdateProfileRequest requestBody) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse submitted = employeeService.submitProfile(employeeId, requestBody);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile submitted successfully and locked for review",
                    "profileStatus", submitted.getProfileStatus().name(),
                    "formCompleted", true,
                    "isFormCompleted", true,
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
     * Optionally saves draft profile fields sent alongside the file without completing submission.
     * Returns 403 Forbidden if profileStatus == SUBMITTED.
     */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestPart(value = "request", required = false) UpdateProfileRequest draftRequest) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse profile = employeeService.uploadDocumentByEmployee(employeeId, file, draftRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Document uploaded successfully",
                    "fileName", profile.getAadhaarFileName() != null ? profile.getAadhaarFileName() : "",
                    "aadhaarFileName", profile.getAadhaarFileName() != null ? profile.getAadhaarFileName() : "",
                    "aadhaarDocumentUrl", profile.getAadhaarDocumentUrl() != null ? profile.getAadhaarDocumentUrl() : "",
                    "aadhaarFileSize", profile.getAadhaarFileSize() != null ? profile.getAadhaarFileSize() : 0L,
                    "documentStatus", profile.getDocumentStatus().name(),
                    "profileStatus", profile.getProfileStatus().name(),
                    "formCompleted", false,
                    "isFormCompleted", false,
                    "profile", profile
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
