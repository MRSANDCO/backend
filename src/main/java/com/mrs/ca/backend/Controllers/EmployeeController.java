package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.EmployeeProfileResponse;
import com.mrs.ca.backend.dto.UpdateProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * Accepts PUT, POST, or PATCH requests.
     * Returns 403 Forbidden if profileStatus == SUBMITTED.
     */
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> updateMyProfile(@RequestBody UpdateProfileRequest request) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse updated = employeeService.updateProfileByEmployee(employeeId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile updated successfully",
                    "success", true,
                    "profileStatus", updated.getProfileStatus().name(),
                    "formCompleted", false,
                    "isFormCompleted", false,
                    "profile", updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /**
     * Employee submits complete profile with final form payload (JSON). Locks profile from further edits.
     */
    @RequestMapping(
            value = {"/submit", "/final-submit", "/submit-profile"},
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> submitMyProfileJson(@RequestBody(required = false) UpdateProfileRequest requestBody) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            EmployeeProfileResponse submitted = employeeService.submitProfile(employeeId, requestBody);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile submitted successfully and locked for review",
                    "success", true,
                    "profileStatus", submitted.getProfileStatus().name(),
                    "formCompleted", true,
                    "isFormCompleted", true,
                    "profile", submitted
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /**
     * Employee submits complete profile via single multipart request containing form fields and optional Aadhaar PDF.
     */
    @RequestMapping(
            value = {"/submit", "/final-submit", "/submit-profile"},
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> submitMyProfileMultipart(
            @RequestParam Map<String, String> allParams,
            @RequestParam(value = "request", required = false) String jsonRequestPart,
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @RequestParam(value = "aadhaarFile", required = false) MultipartFile aadhaarFileParam,
            @RequestParam(value = "document", required = false) MultipartFile documentParam,
            @RequestParam(value = "aadhaar_file", required = false) MultipartFile aadhaarFileSnakeParam,
            @RequestParam(value = "idProof", required = false) MultipartFile idProofParam
    ) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            MultipartFile fileToUse = fileParam != null ? fileParam :
                    (aadhaarFileParam != null ? aadhaarFileParam :
                    (documentParam != null ? documentParam :
                    (aadhaarFileSnakeParam != null ? aadhaarFileSnakeParam : idProofParam)));

            UpdateProfileRequest req = buildUpdateRequestFromParams(allParams, jsonRequestPart);

            EmployeeProfileResponse submitted = employeeService.submitProfile(employeeId, req, fileToUse);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile submitted successfully and locked for review",
                    "success", true,
                    "profileStatus", submitted.getProfileStatus().name(),
                    "formCompleted", true,
                    "isFormCompleted", true,
                    "profile", submitted
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store document: " + e.getMessage(), "success", false));
        }
    }

    /**
     * Fallback submit endpoint for form-urlencoded or unspecified content types.
     */
    @RequestMapping(
            value = {"/submit", "/final-submit", "/submit-profile"},
            method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public ResponseEntity<?> submitMyProfileFallback(@RequestParam Map<String, String> allParams) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            UpdateProfileRequest req = buildUpdateRequestFromParams(allParams, null);
            EmployeeProfileResponse submitted = employeeService.submitProfile(employeeId, req);
            return ResponseEntity.ok(Map.of(
                    "message", "Profile submitted successfully and locked for review",
                    "success", true,
                    "profileStatus", submitted.getProfileStatus().name(),
                    "formCompleted", true,
                    "isFormCompleted", true,
                    "profile", submitted
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /**
     * Employee uploads Aadhaar / ID proof PDF (standalone upload if done prior to submit).
     * Returns 403 Forbidden if profileStatus == SUBMITTED.
     */
    @PostMapping(value = {"/document", "/upload", "/upload-document"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @RequestParam(value = "aadhaarFile", required = false) MultipartFile aadhaarFileParam,
            @RequestParam(value = "document", required = false) MultipartFile documentParam
    ) {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            MultipartFile fileToUse = fileParam != null ? fileParam :
                    (aadhaarFileParam != null ? aadhaarFileParam : documentParam);
            if (fileToUse == null || fileToUse.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No PDF file was provided", "success", false));
            }
            EmployeeProfileResponse profile = employeeService.uploadDocumentByEmployee(employeeId, fileToUse);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.ofEntries(
                    Map.entry("message", "Document uploaded successfully"),
                    Map.entry("success", true),
                    Map.entry("fileName", profile.getAadhaarFileName() != null ? profile.getAadhaarFileName() : ""),
                    Map.entry("aadhaarFileName", profile.getAadhaarFileName() != null ? profile.getAadhaarFileName() : ""),
                    Map.entry("aadhaarDocumentUrl", profile.getAadhaarDocumentUrl() != null ? profile.getAadhaarDocumentUrl() : ""),
                    Map.entry("aadhaarFileSize", profile.getAadhaarFileSize() != null ? profile.getAadhaarFileSize() : 0L),
                    Map.entry("documentStatus", profile.getDocumentStatus().name()),
                    Map.entry("profileStatus", profile.getProfileStatus().name()),
                    Map.entry("formCompleted", false),
                    Map.entry("isFormCompleted", false),
                    Map.entry("profile", profile)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store document: " + e.getMessage(), "success", false));
        }
    }

    private UpdateProfileRequest buildUpdateRequestFromParams(Map<String, String> params, String jsonPart) {
        UpdateProfileRequest req = new UpdateProfileRequest();
        if (jsonPart != null && !jsonPart.trim().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.findAndRegisterModules();
                req = mapper.readValue(jsonPart, UpdateProfileRequest.class);
            } catch (Exception e) {
                // Ignore json parse error and proceed with param mapping
            }
        }
        if (params != null && !params.isEmpty()) {
            if (params.containsKey("name")) req.setName(params.get("name"));
            if (params.containsKey("full_name")) req.setName(params.get("full_name"));
            if (params.containsKey("fullName")) req.setName(params.get("fullName"));

            if (params.containsKey("fatherName")) req.setFatherName(params.get("fatherName"));
            if (params.containsKey("father_name")) req.setFatherName(params.get("father_name"));

            if (params.containsKey("mobileNumber")) req.setMobileNumber(params.get("mobileNumber"));
            if (params.containsKey("mobile_number")) req.setMobileNumber(params.get("mobile_number"));
            if (params.containsKey("phone")) req.setMobileNumber(params.get("phone"));
            if (params.containsKey("mobile")) req.setMobileNumber(params.get("mobile"));

            if (params.containsKey("fatherMobileNumber")) req.setFatherMobileNumber(params.get("fatherMobileNumber"));
            if (params.containsKey("father_mobile_number")) req.setFatherMobileNumber(params.get("father_mobile_number"));
            if (params.containsKey("father_mobile")) req.setFatherMobileNumber(params.get("father_mobile"));

            if (params.containsKey("aadhaarNumber")) req.setAadhaarNumber(params.get("aadhaarNumber"));
            if (params.containsKey("aadhaar_number")) req.setAadhaarNumber(params.get("aadhaar_number"));
            if (params.containsKey("aadhaar")) req.setAadhaarNumber(params.get("aadhaar"));

            if (params.containsKey("panNumber")) req.setPanNumber(params.get("panNumber"));
            if (params.containsKey("pan_number")) req.setPanNumber(params.get("pan_number"));
            if (params.containsKey("pan")) req.setPanNumber(params.get("pan"));

            if (params.containsKey("email")) req.setEmail(params.get("email"));
            if (params.containsKey("emailAddress")) req.setEmail(params.get("emailAddress"));
            if (params.containsKey("email_address")) req.setEmail(params.get("email_address"));

            if (params.containsKey("addressLine1")) req.setAddressLine1(params.get("addressLine1"));
            if (params.containsKey("address_line1")) req.setAddressLine1(params.get("address_line1"));
            if (params.containsKey("address1")) req.setAddressLine1(params.get("address1"));

            if (params.containsKey("addressLine2")) req.setAddressLine2(params.get("addressLine2"));
            if (params.containsKey("address_line2")) req.setAddressLine2(params.get("address_line2"));
            if (params.containsKey("address2")) req.setAddressLine2(params.get("address2"));

            if (params.containsKey("city")) req.setCity(params.get("city"));
            if (params.containsKey("state")) req.setState(params.get("state"));

            if (params.containsKey("pinCode")) req.setPinCode(params.get("pinCode"));
            if (params.containsKey("pin_code")) req.setPinCode(params.get("pin_code"));
            if (params.containsKey("pincode")) req.setPinCode(params.get("pincode"));

            if (params.containsKey("resumeGoogleDriveLink")) req.setResumeGoogleDriveLink(params.get("resumeGoogleDriveLink"));
            if (params.containsKey("resume_google_drive_link")) req.setResumeGoogleDriveLink(params.get("resume_google_drive_link"));
            if (params.containsKey("resume_link")) req.setResumeGoogleDriveLink(params.get("resume_link"));
            if (params.containsKey("resumeLink")) req.setResumeGoogleDriveLink(params.get("resumeLink"));

            if (params.containsKey("referredBy")) req.setReferredBy(params.get("referredBy"));
            if (params.containsKey("referred_by")) req.setReferredBy(params.get("referred_by"));
            if (params.containsKey("reference")) req.setReferredBy(params.get("reference"));

            if (params.containsKey("permanentAddress")) req.setPermanentAddress(params.get("permanentAddress"));
            if (params.containsKey("permanent_address")) req.setPermanentAddress(params.get("permanent_address"));

            if (params.containsKey("currentAddress")) req.setCurrentAddress(params.get("currentAddress"));
            if (params.containsKey("current_address")) req.setCurrentAddress(params.get("current_address"));
        }
        return req;
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
