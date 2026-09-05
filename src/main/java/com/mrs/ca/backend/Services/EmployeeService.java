package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.DocumentVerificationStatus;
import com.mrs.ca.backend.Models.Employee;
import com.mrs.ca.backend.Models.ProfileStatus;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.*;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^\\d{12}$");
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           SequenceGeneratorService sequenceGeneratorService,
                           GridFsTemplate gridFsTemplate,
                           GridFsOperations gridFsOperations) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.gridFsTemplate = gridFsTemplate;
        this.gridFsOperations = gridFsOperations;
    }

    // =========================================================================
    // Admin Operations
    // =========================================================================

    /**
     * Admin creates an employee with Name and Mobile Number.
     * Generates immutable Employee ID, User account with role EMPLOYEE, and initial profile.
     */
    public CreateEmployeeResponse createEmployee(CreateEmployeeRequest request, String adminUsername) {
        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new IllegalArgumentException("Employee name is required");
        }
        String name = request.getName().trim();

        if (request.getMobileNumber() == null || request.getMobileNumber().trim().isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        String mobile = cleanMobile(request.getMobileNumber());
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new IllegalArgumentException("Invalid mobile number. Must be a valid 10-digit Indian mobile number");
        }

        if (employeeRepository.existsByMobileNumber(mobile)) {
            throw new IllegalArgumentException("An employee with mobile number '" + mobile + "' already exists");
        }

        // 1. Generate unique Employee ID
        String employeeId = sequenceGeneratorService.generateNextEmployeeId();

        // 2. Generate initial password
        String initialPassword = (request.getInitialPassword() != null && !request.getInitialPassword().trim().isBlank())
                ? request.getInitialPassword().trim()
                : generateSecurePassword(10);

        // 3. Create User account for authentication
        User user = new User();
        user.setUserId(employeeId);
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setFullName(name);
        user.setPhone(mobile);
        user.setRole("EMPLOYEE");
        user.setActive(true);
        user.setCreatedByAdmin(adminUsername != null ? adminUsername : "admin");
        userRepository.save(user);

        // 4. Create Employee entity
        Employee employee = new Employee(employeeId, name, mobile);
        employee.setProfileStatus(ProfileStatus.INCOMPLETE);
        employee.setDocumentStatus(DocumentVerificationStatus.PENDING);
        employeeRepository.save(employee);

        log.info("[EMPLOYEE] Created employee: employeeId='{}', name='{}'", employeeId, name);

        return new CreateEmployeeResponse(
                employeeId,
                name,
                mobile,
                initialPassword,
                ProfileStatus.INCOMPLETE,
                "Employee created successfully. Share the initial credentials with the employee."
        );
    }

    /**
     * Admin lists/searches employees with pagination.
     */
    public Page<EmployeeProfileResponse> getAllEmployees(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "created_at"));

        Page<Employee> employeePage;
        if (search != null && !search.trim().isBlank()) {
            employeePage = employeeRepository.searchEmployees(search.trim(), pageable);
        } else {
            employeePage = employeeRepository.findAll(pageable);
        }

        List<EmployeeProfileResponse> dtoList = employeePage.getContent().stream()
                .map(this::toProfileResponse)
                .toList();

        return new PageImpl<>(dtoList, pageable, employeePage.getTotalElements());
    }

    /**
     * Admin retrieves single employee profile by employeeId.
     */
    public EmployeeProfileResponse getEmployeeByIdForAdmin(String employeeId) {
        Employee employee = findEmployeeOrThrow(employeeId);
        return toProfileResponse(employee);
    }

    /**
     * Admin edits employee profile (allowed even if profileStatus is SUBMITTED).
     */
    public EmployeeProfileResponse updateEmployeeByAdmin(String employeeId, UpdateProfileRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);

        applyProfileUpdates(employee, request);
        Employee saved = employeeRepository.save(employee);

        // Sync User record if name or mobile changed
        userRepository.findByUserId(employeeId).ifPresent(user -> {
            if (request.getName() != null && !request.getName().trim().isBlank()) {
                user.setFullName(request.getName().trim());
            }
            if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isBlank()) {
                user.setPhone(cleanMobile(request.getMobileNumber()));
            }
            userRepository.save(user);
        });

        log.info("[ADMIN] Updated profile for employeeId='{}'", employeeId);
        return toProfileResponse(saved);
    }

    /**
     * Admin resets employee password.
     */
    public String resetEmployeePassword(String employeeId, String newPassword) {
        User user = userRepository.findByUserId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee user account not found for '" + employeeId + "'"));

        String passwordToSet = (newPassword != null && !newPassword.trim().isBlank())
                ? newPassword.trim()
                : generateSecurePassword(10);

        user.setPassword(passwordEncoder.encode(passwordToSet));
        userRepository.save(user);

        log.info("[ADMIN] Reset password for employeeId='{}'", employeeId);
        return passwordToSet;
    }

    /**
     * Admin activates or deactivates an employee account.
     */
    public void setEmployeeActiveStatus(String employeeId, boolean active) {
        User user = userRepository.findByUserId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee user account not found for '" + employeeId + "'"));
        user.setActive(active);
        userRepository.save(user);
        log.info("[ADMIN] Set active status to '{}' for employeeId='{}'", active, employeeId);
    }

    /**
     * Admin marks uploaded Aadhaar/ID document as VERIFIED.
     */
    public EmployeeProfileResponse verifyDocument(String employeeId) {
        Employee employee = findEmployeeOrThrow(employeeId);
        if (employee.getAadhaarGridFsId() == null) {
            throw new IllegalArgumentException("No document has been uploaded yet for employee '" + employeeId + "'");
        }

        employee.setDocumentStatus(DocumentVerificationStatus.VERIFIED);
        employee.setDocumentRejectionReason(null);
        Employee saved = employeeRepository.save(employee);
        log.info("[ADMIN] Verified document for employeeId='{}'", employeeId);
        return toProfileResponse(saved);
    }

    /**
     * Admin marks uploaded Aadhaar/ID document as REJECTED with a reason.
     */
    public EmployeeProfileResponse rejectDocument(String employeeId, String reason) {
        Employee employee = findEmployeeOrThrow(employeeId);
        if (employee.getAadhaarGridFsId() == null) {
            throw new IllegalArgumentException("No document has been uploaded yet for employee '" + employeeId + "'");
        }

        employee.setDocumentStatus(DocumentVerificationStatus.REJECTED);
        employee.setDocumentRejectionReason(reason != null && !reason.trim().isBlank() ? reason.trim() : "Rejected by Admin");
        Employee saved = employeeRepository.save(employee);
        log.info("[ADMIN] Rejected document for employeeId='{}'", employeeId);
        return toProfileResponse(saved);
    }

    // =========================================================================
    // Employee Self-Service Operations
    // =========================================================================

    /**
     * Employee views their own profile.
     */
    public EmployeeProfileResponse getProfileForEmployee(String employeeId) {
        Employee employee = findEmployeeOrThrow(employeeId);
        return toProfileResponse(employee);
    }

    /**
     * Employee updates their own profile before submission.
     * Must reject with 403 SecurityException if profileStatus == SUBMITTED.
     */
    public EmployeeProfileResponse updateProfileByEmployee(String employeeId, UpdateProfileRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);

        if (employee.getProfileStatus() == ProfileStatus.SUBMITTED) {
            log.warn("[ACCESS DENIED] Employee '{}' attempted to modify already submitted profile", employeeId);
            throw new SecurityException("Profile has been submitted and is locked for editing. Please contact Admin for updates.");
        }

        applyProfileUpdates(employee, request);
        Employee saved = employeeRepository.save(employee);

        // Keep User record in sync
        userRepository.findByUserId(employeeId).ifPresent(user -> {
            if (request.getName() != null && !request.getName().trim().isBlank()) {
                user.setFullName(request.getName().trim());
            }
            if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isBlank()) {
                user.setPhone(cleanMobile(request.getMobileNumber()));
            }
            userRepository.save(user);
        });

        log.info("[EMPLOYEE] Updated profile for employeeId='{}'", employeeId);
        return toProfileResponse(saved);
    }

    /**
     * Employee submits complete profile.
     * Validates all required profile fields and document upload.
     */
    public EmployeeProfileResponse submitProfile(String employeeId) {
        Employee employee = findEmployeeOrThrow(employeeId);

        if (employee.getProfileStatus() == ProfileStatus.SUBMITTED) {
            throw new SecurityException("Profile is already submitted.");
        }

        // Validate completeness
        validateCompletenessForSubmission(employee);

        employee.setProfileStatus(ProfileStatus.SUBMITTED);
        Employee saved = employeeRepository.save(employee);

        log.info("[EMPLOYEE] Successfully submitted complete profile for employeeId='{}'", employeeId);
        return toProfileResponse(saved);
    }

    /**
     * Employee uploads Aadhaar / ID proof PDF.
     * Must reject with 403 SecurityException if profileStatus == SUBMITTED.
     */
    public EmployeeProfileResponse uploadDocumentByEmployee(String employeeId, MultipartFile file) throws IOException {
        Employee employee = findEmployeeOrThrow(employeeId);

        if (employee.getProfileStatus() == ProfileStatus.SUBMITTED) {
            log.warn("[ACCESS DENIED] Employee '{}' attempted to upload document after profile submission", employeeId);
            throw new SecurityException("Profile has already been submitted. Documents cannot be modified or replaced. Contact Admin.");
        }

        validatePdfFile(file);

        // If replacing an unverified existing file, delete old GridFS binary
        if (employee.getAadhaarGridFsId() != null && !employee.getAadhaarGridFsId().isBlank()) {
            gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(employee.getAadhaarGridFsId()))));
        }

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "aadhaar_document.pdf";

        ObjectId gridFsId;
        try (InputStream is = file.getInputStream()) {
            gridFsId = gridFsTemplate.store(is, originalFileName, "application/pdf");
        }

        employee.setAadhaarGridFsId(gridFsId.toHexString());
        employee.setAadhaarFileName(originalFileName);
        employee.setAadhaarFileSize(file.getSize());
        employee.setAadhaarDocumentUrl("/api/employee/profile/document");
        employee.setDocumentStatus(DocumentVerificationStatus.PENDING);
        employee.setDocumentRejectionReason(null);

        Employee saved = employeeRepository.save(employee);
        log.info("[EMPLOYEE] Uploaded Aadhaar PDF document for employeeId='{}' (GridFS ID: {})",
                employeeId, gridFsId.toHexString());

        return toProfileResponse(saved);
    }

    /**
     * Authenticate employee credentials against User and Employee records.
     */
    public java.util.Optional<Employee> authenticateEmployee(String employeeId, String password) {
        if (employeeId == null || password == null) return java.util.Optional.empty();
        java.util.Optional<User> userOpt = userRepository.findByUserId(employeeId);
        if (userOpt.isEmpty()) return java.util.Optional.empty();

        User user = userOpt.get();
        if (!user.isActive()) {
            log.warn("[AUTH] Inactive employee login attempt for employeeId='{}'", employeeId);
            return java.util.Optional.empty();
        }
        if (!"EMPLOYEE".equalsIgnoreCase(user.getRole())) {
            log.warn("[AUTH] Non-employee role login attempt at employee endpoint for employeeId='{}'", employeeId);
            return java.util.Optional.empty();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[AUTH] Failed employee login attempt for employeeId='{}'", employeeId);
            return java.util.Optional.empty();
        }

        return employeeRepository.findByEmployeeId(employeeId);
    }

    /**
     * Stream document binary to HTTP response.
     * Enforces that non-admin callers can only stream their own document.
     */
    public void streamDocument(String employeeId, String requestingUserId, boolean isAdmin, HttpServletResponse response)
            throws IOException {
        if (!isAdmin && !employeeId.equals(requestingUserId)) {
            throw new SecurityException("Access denied: You can only access your own document");
        }

        Employee employee = findEmployeeOrThrow(employeeId);
        if (employee.getAadhaarGridFsId() == null || employee.getAadhaarGridFsId().isBlank()) {
            throw new IllegalArgumentException("No document found for employee '" + employeeId + "'");
        }

        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(employee.getAadhaarGridFsId())))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("Document file not found in storage");
        }

        response.setContentType("application/pdf");
        String filename = employee.getAadhaarFileName() != null ? employee.getAadhaarFileName() : "document.pdf";
        response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

        if (employee.getAadhaarFileSize() != null) {
            response.setContentLengthLong(employee.getAadhaarFileSize());
        }

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    // =========================================================================
    // Helpers & Validation
    // =========================================================================

    public Employee findEmployeeOrThrow(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID '" + employeeId + "' not found"));
    }

    private void applyProfileUpdates(Employee employee, UpdateProfileRequest request) {
        if (request.getName() != null && !request.getName().trim().isBlank()) {
            employee.setName(request.getName().trim());
        }
        if (request.getFatherName() != null && !request.getFatherName().trim().isBlank()) {
            employee.setFatherName(request.getFatherName().trim());
        }
        if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isBlank()) {
            String mobile = cleanMobile(request.getMobileNumber());
            if (!MOBILE_PATTERN.matcher(mobile).matches()) {
                throw new IllegalArgumentException("Invalid mobile number. Must be 10 digits");
            }
            employee.setMobileNumber(mobile);
        }
        if (request.getAadhaarNumber() != null && !request.getAadhaarNumber().trim().isBlank()) {
            String aadhaar = request.getAadhaarNumber().replaceAll("\\s+", "");
            if (!AADHAAR_PATTERN.matcher(aadhaar).matches()) {
                throw new IllegalArgumentException("Invalid Aadhaar number. Must be exactly 12 digits");
            }
            employee.setAadhaarNumber(aadhaar);
        }
        if (request.getPanNumber() != null && !request.getPanNumber().trim().isBlank()) {
            String pan = request.getPanNumber().trim().toUpperCase();
            if (!PAN_PATTERN.matcher(pan).matches()) {
                throw new IllegalArgumentException("Invalid PAN number format (e.g. ABCDE1234F)");
            }
            employee.setPanNumber(pan);
        }
        if (request.getDateOfJoining() != null) {
            employee.setDateOfJoining(request.getDateOfJoining());
        }
        if (request.getPermanentAddress() != null && !request.getPermanentAddress().trim().isBlank()) {
            employee.setPermanentAddress(request.getPermanentAddress().trim());
        }
        if (request.getCurrentAddress() != null && !request.getCurrentAddress().trim().isBlank()) {
            employee.setCurrentAddress(request.getCurrentAddress().trim());
        }
    }

    private void validateCompletenessForSubmission(Employee employee) {
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new IllegalArgumentException("Full Name is required for profile submission");
        }
        if (employee.getFatherName() == null || employee.getFatherName().isBlank()) {
            throw new IllegalArgumentException("Father's Name is required for profile submission");
        }
        if (employee.getMobileNumber() == null || employee.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile Number is required for profile submission");
        }
        if (employee.getAadhaarNumber() == null || employee.getAadhaarNumber().isBlank()) {
            throw new IllegalArgumentException("Aadhaar Number is required for profile submission");
        }
        if (employee.getPanNumber() == null || employee.getPanNumber().isBlank()) {
            throw new IllegalArgumentException("PAN Number is required for profile submission");
        }
        if (employee.getDateOfJoining() == null) {
            throw new IllegalArgumentException("Date of Joining is required for profile submission");
        }
        if (employee.getPermanentAddress() == null || employee.getPermanentAddress().isBlank()) {
            throw new IllegalArgumentException("Permanent Address is required for profile submission");
        }
        if (employee.getCurrentAddress() == null || employee.getCurrentAddress().isBlank()) {
            throw new IllegalArgumentException("Current Address is required for profile submission");
        }
        if (employee.getAadhaarGridFsId() == null || employee.getAadhaarGridFsId().isBlank()) {
            throw new IllegalArgumentException("Aadhaar/ID Proof PDF document must be uploaded before submitting profile");
        }
    }

    private void validatePdfFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed (.pdf extension required)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Invalid content type. Expected application/pdf");
        }

        // Verify PDF Magic Bytes (%PDF-)
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("File is corrupt or too small to be a valid PDF");
            }
            String magic = new String(header, 0, read, StandardCharsets.US_ASCII);
            if (!magic.startsWith("%PDF")) {
                throw new IllegalArgumentException("File does not contain a valid PDF signature");
            }
        }
    }

    private EmployeeProfileResponse toProfileResponse(Employee employee) {
        EmployeeProfileResponse dto = new EmployeeProfileResponse();
        dto.setId(employee.getId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setName(employee.getName());
        dto.setFatherName(employee.getFatherName());
        dto.setMobileNumber(employee.getMobileNumber());
        dto.setAadhaarNumber(employee.getAadhaarNumber());
        dto.setPanNumber(employee.getPanNumber());
        dto.setDateOfJoining(employee.getDateOfJoining());
        dto.setPermanentAddress(employee.getPermanentAddress());
        dto.setCurrentAddress(employee.getCurrentAddress());
        dto.setAadhaarDocumentUrl(employee.getAadhaarDocumentUrl());
        dto.setAadhaarFileName(employee.getAadhaarFileName());
        dto.setAadhaarFileSize(employee.getAadhaarFileSize());
        dto.setProfileStatus(employee.getProfileStatus());
        dto.setDocumentStatus(employee.getDocumentStatus());
        dto.setDocumentRejectionReason(employee.getDocumentRejectionReason());
        dto.setCreatedAt(employee.getCreatedAt());
        dto.setUpdatedAt(employee.getUpdatedAt());

        userRepository.findByUserId(employee.getEmployeeId()).ifPresent(user -> dto.setActive(user.isActive()));

        return dto;
    }

    private String cleanMobile(String mobile) {
        String cleaned = mobile.replaceAll("[^0-9]", "");
        if (cleaned.length() == 12 && cleaned.startsWith("91")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned;
    }

    private String generateSecurePassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
