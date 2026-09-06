package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.*;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SequenceGeneratorService sequenceGeneratorService;
    @Mock private GridFsTemplate gridFsTemplate;
    @Mock private GridFsOperations gridFsOperations;

    @InjectMocks private EmployeeService employeeService;

    private Employee testEmployee;
    private User testUser;

    @BeforeEach
    void setUp() {
        testEmployee = new Employee("EMP1001", "Alice Sharma", "9876543210");
        testEmployee.setId("emp-mongo-id");
        testEmployee.setProfileStatus(ProfileStatus.INCOMPLETE);
        testEmployee.setDocumentStatus(DocumentVerificationStatus.PENDING);

        testUser = new User();
        testUser.setUserId("EMP1001");
        testUser.setPassword("hashedPass");
        testUser.setFullName("Alice Sharma");
        testUser.setPhone("9876543210");
        testUser.setRole("EMPLOYEE");
        testUser.setActive(true);
    }

    // =========================================================================
    // Employee Creation
    // =========================================================================

    @Nested
    @DisplayName("createEmployee")
    class CreateEmployeeTests {

        @Test
        @DisplayName("successfully creates employee with auto-generated ID and hashed password")
        void createEmployee_success() {
            CreateEmployeeRequest req = new CreateEmployeeRequest("Alice Sharma", "9876543210");
            when(sequenceGeneratorService.generateNextEmployeeId()).thenReturn("EMP1001");
            when(employeeRepository.existsByMobileNumber("9876543210")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedSecurePassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CreateEmployeeResponse response = employeeService.createEmployee(req, "admin");

            assertThat(response.getEmployeeId()).isEqualTo("EMP1001");
            assertThat(response.getName()).isEqualTo("Alice Sharma");
            assertThat(response.getMobileNumber()).isEqualTo("9876543210");
            assertThat(response.getInitialPassword()).isNotBlank();
            assertThat(response.getProfileStatus()).isEqualTo(ProfileStatus.INCOMPLETE);

            verify(userRepository).save(argThat(user ->
                    "EMP1001".equals(user.getUserId()) &&
                    "EMPLOYEE".equals(user.getRole()) &&
                    user.isActive()
            ));
        }

        @Test
        @DisplayName("fails when mobile number already exists")
        void createEmployee_duplicateMobile_throwsException() {
            CreateEmployeeRequest req = new CreateEmployeeRequest("Bob", "9876543210");
            when(employeeRepository.existsByMobileNumber("9876543210")).thenReturn(true);

            assertThatThrownBy(() -> employeeService.createEmployee(req, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("fails when mobile number is invalid")
        void createEmployee_invalidMobile_throwsException() {
            CreateEmployeeRequest req = new CreateEmployeeRequest("Bob", "12345");

            assertThatThrownBy(() -> employeeService.createEmployee(req, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid mobile number");
        }
    }

    // =========================================================================
    // Profile Updates & Submission (Permission Enforcement)
    // =========================================================================

    @Nested
    @DisplayName("updateProfileByEmployee & submitProfile")
    class ProfileUpdatesAndSubmissionTests {

        @Test
        @DisplayName("employee can update profile while INCOMPLETE")
        void updateProfile_whenIncomplete_success() {
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findByUserId("EMP1001")).thenReturn(Optional.of(testUser));

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFatherName("Rajesh Sharma");
            req.setAadhaarNumber("123456789012");
            req.setPanNumber("ABCDE1234F");
            req.setDateOfJoining(LocalDate.of(2025, 1, 15));
            req.setPermanentAddress("123 Main St, Delhi");
            req.setCurrentAddress("456 Park Ave, Delhi");

            EmployeeProfileResponse response = employeeService.updateProfileByEmployee("EMP1001", req);

            assertThat(response.getFatherName()).isEqualTo("Rajesh Sharma");
            assertThat(response.getAadhaarNumber()).isEqualTo("123456789012");
            assertThat(response.getPanNumber()).isEqualTo("ABCDE1234F");
        }

        @Test
        @DisplayName("employee CANNOT update profile once SUBMITTED (403 SecurityException)")
        void updateProfile_whenSubmitted_throwsForbidden() {
            testEmployee.setProfileStatus(ProfileStatus.SUBMITTED);
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFatherName("Attempted Change");

            assertThatThrownBy(() -> employeeService.updateProfileByEmployee("EMP1001", req))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Profile has been submitted and is locked");
        }

        @Test
        @DisplayName("employee can submit complete profile with all fields and document uploaded")
        void submitProfile_complete_success() {
            testEmployee.setFatherName("Rajesh Sharma");
            testEmployee.setFatherMobileNumber("9876543211");
            testEmployee.setAadhaarNumber("123456789012");
            testEmployee.setPanNumber("ABCDE1234F");
            testEmployee.setDateOfJoining(LocalDate.of(2025, 1, 15));
            testEmployee.setPermanentAddress("123 Main St");
            testEmployee.setCurrentAddress("456 Park Ave");
            testEmployee.setAadhaarGridFsId("gridfs-id-123");

            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            EmployeeProfileResponse response = employeeService.submitProfile("EMP1001");

            assertThat(response.getProfileStatus()).isEqualTo(ProfileStatus.SUBMITTED);
        }

        @Test
        @DisplayName("employee submission fails if mandatory document is missing")
        void submitProfile_missingDoc_throwsBadRequest() {
            testEmployee.setFatherName("Rajesh Sharma");
            testEmployee.setFatherMobileNumber("9876543211");
            testEmployee.setAadhaarNumber("123456789012");
            testEmployee.setPanNumber("ABCDE1234F");
            testEmployee.setDateOfJoining(LocalDate.of(2025, 1, 15));
            testEmployee.setPermanentAddress("123 Main St");
            testEmployee.setCurrentAddress("456 Park Ave");
            testEmployee.setAadhaarGridFsId(null); // No document uploaded

            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));

            assertThatThrownBy(() -> employeeService.submitProfile("EMP1001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("document must be uploaded");
        }

        @Test
        @DisplayName("Admin can update submitted profile")
        void adminUpdate_whenSubmitted_success() {
            testEmployee.setProfileStatus(ProfileStatus.SUBMITTED);
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findByUserId("EMP1001")).thenReturn(Optional.of(testUser));

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setCurrentAddress("Updated by Admin Address");

            EmployeeProfileResponse response = employeeService.updateEmployeeByAdmin("EMP1001", req);

            assertThat(response.getCurrentAddress()).isEqualTo("Updated by Admin Address");
        }
    }

    // =========================================================================
    // Document Upload & Validation
    // =========================================================================

    @Nested
    @DisplayName("uploadDocumentByEmployee")
    class DocumentUploadTests {

        @Test
        @DisplayName("successfully uploads valid PDF when profile is INCOMPLETE")
        void uploadValidPdf_success() throws IOException {
            byte[] validPdfContent = "%PDF-1.4\nsome pdf binary data".getBytes();
            MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", validPdfContent);

            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(gridFsTemplate.store(any(InputStream.class), eq("aadhaar.pdf"), eq("application/pdf")))
                    .thenReturn(new ObjectId());
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            EmployeeProfileResponse response = employeeService.uploadDocumentByEmployee("EMP1001", file);

            assertThat(response.getAadhaarFileName()).isEqualTo("aadhaar.pdf");
            assertThat(response.getDocumentStatus()).isEqualTo(DocumentVerificationStatus.PENDING);
        }

        @Test
        @DisplayName("rejects document upload when profile is already SUBMITTED (403 SecurityException)")
        void uploadPdf_whenSubmitted_throwsForbidden() {
            testEmployee.setProfileStatus(ProfileStatus.SUBMITTED);
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));

            byte[] validPdfContent = "%PDF-1.4\nsome pdf binary data".getBytes();
            MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", validPdfContent);

            assertThatThrownBy(() -> employeeService.uploadDocumentByEmployee("EMP1001", file))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Profile has already been submitted");
        }

        @Test
        @DisplayName("rejects non-PDF extension")
        void uploadNonPdfExtension_throwsBadRequest() {
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));

            MockMultipartFile file = new MockMultipartFile("file", "aadhaar.jpg", "image/jpeg", "fake".getBytes());

            assertThatThrownBy(() -> employeeService.uploadDocumentByEmployee("EMP1001", file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only PDF files are allowed");
        }

        @Test
        @DisplayName("rejects spoofed PDF without %PDF- magic bytes")
        void uploadCorruptPdfHeader_throwsBadRequest() {
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));

            byte[] corruptPdf = "NOTAPDF_HEADER".getBytes();
            MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", corruptPdf);

            assertThatThrownBy(() -> employeeService.uploadDocumentByEmployee("EMP1001", file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not contain a valid PDF signature");
        }
    }

    // =========================================================================
    // Admin Verification & Password Reset
    // =========================================================================

    @Nested
    @DisplayName("Admin actions: verify, reject, reset password, status")
    class AdminActionsTests {

        @Test
        @DisplayName("admin verifies uploaded document")
        void verifyDocument_success() {
            testEmployee.setAadhaarGridFsId("fs-123");
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            EmployeeProfileResponse res = employeeService.verifyDocument("EMP1001");
            assertThat(res.getDocumentStatus()).isEqualTo(DocumentVerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("admin rejects document with reason")
        void rejectDocument_success() {
            testEmployee.setAadhaarGridFsId("fs-123");
            when(employeeRepository.findByEmployeeId("EMP1001")).thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

            EmployeeProfileResponse res = employeeService.rejectDocument("EMP1001", "Blurry image");
            assertThat(res.getDocumentStatus()).isEqualTo(DocumentVerificationStatus.REJECTED);
            assertThat(res.getDocumentRejectionReason()).isEqualTo("Blurry image");
        }

        @Test
        @DisplayName("admin resets employee password")
        void resetPassword_success() {
            when(userRepository.findByUserId("EMP1001")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("NewSecret123")).thenReturn("encodedNewSecret");

            String newPass = employeeService.resetEmployeePassword("EMP1001", "NewSecret123");
            assertThat(newPass).isEqualTo("NewSecret123");
            verify(userRepository).save(argThat(user -> "encodedNewSecret".equals(user.getPassword())));
        }

        @Test
        @DisplayName("admin deactivates employee account")
        void deactivateAccount_success() {
            when(userRepository.findByUserId("EMP1001")).thenReturn(Optional.of(testUser));

            employeeService.setEmployeeActiveStatus("EMP1001", false);
            assertThat(testUser.isActive()).isFalse();
            verify(userRepository).save(testUser);
        }
    }
}
