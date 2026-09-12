package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.DocumentVerificationStatus;
import com.mrs.ca.backend.Models.ProfileStatus;
import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.EmployeeProfileResponse;
import com.mrs.ca.backend.dto.UpdateProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class EmployeeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EmployeeService employeeService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("GET /api/employee/profile — 200 returns own profile")
    void getProfile_success() throws Exception {
        EmployeeProfileResponse res = new EmployeeProfileResponse();
        res.setEmployeeId("EMP1001");
        res.setName("John Doe");
        res.setProfileStatus(ProfileStatus.INCOMPLETE);

        when(employeeService.getProfileForEmployee("EMP1001")).thenReturn(res);

        mockMvc.perform(get("/api/employee/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("EMP1001"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("PUT /api/employee/profile — 200 when INCOMPLETE")
    void updateProfile_success() throws Exception {
        EmployeeProfileResponse res = new EmployeeProfileResponse();
        res.setEmployeeId("EMP1001");
        res.setFatherName("Bob Doe");
        res.setProfileStatus(ProfileStatus.INCOMPLETE);

        when(employeeService.updateProfileByEmployee(eq("EMP1001"), any(UpdateProfileRequest.class)))
                .thenReturn(res);

        mockMvc.perform(put("/api/employee/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fatherName":"Bob Doe"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.fatherName").value("Bob Doe"));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("PUT /api/employee/profile — 403 Forbidden when profile is already SUBMITTED")
    void updateProfile_whenSubmitted_forbidden() throws Exception {
        when(employeeService.updateProfileByEmployee(eq("EMP1001"), any(UpdateProfileRequest.class)))
                .thenThrow(new SecurityException("Profile has been submitted and is locked for editing"));

        mockMvc.perform(put("/api/employee/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fatherName":"Bob Doe"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Profile has been submitted and is locked for editing"));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("POST /api/employee/profile/submit — 200 successfully locks profile and returns formCompleted=true")
    void submitProfile_success() throws Exception {
        EmployeeProfileResponse res = new EmployeeProfileResponse();
        res.setEmployeeId("EMP1001");
        res.setProfileStatus(ProfileStatus.SUBMITTED);
        res.setFormCompleted(true);

        when(employeeService.submitProfile("EMP1001", null)).thenReturn(res);

        mockMvc.perform(post("/api/employee/profile/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.formCompleted").value(true))
                .andExpect(jsonPath("$.isFormCompleted").value(true));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("POST /api/employee/profile/submit — 200 handles snake_case fields correctly")
    void submitProfile_withSnakeCaseBody_success() throws Exception {
        EmployeeProfileResponse res = new EmployeeProfileResponse();
        res.setEmployeeId("EMP1001");
        res.setFatherName("Rajesh Sharma");
        res.setAadhaarNumber("123456789012");
        res.setProfileStatus(ProfileStatus.SUBMITTED);
        res.setFormCompleted(true);

        when(employeeService.submitProfile(eq("EMP1001"), any(UpdateProfileRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/employee/profile/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "father_name": "Rajesh Sharma",
                                  "aadhaar_number": "123456789012",
                                  "date_of_joining": "2025-01-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.formCompleted").value(true))
                .andExpect(jsonPath("$.profile.fatherName").value("Rajesh Sharma"));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("POST /api/employee/profile/document — 201 Created on valid PDF upload")
    void uploadDocument_success() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nvalid".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", pdfBytes);

        EmployeeProfileResponse res = new EmployeeProfileResponse();
        res.setEmployeeId("EMP1001");
        res.setAadhaarFileName("id.pdf");
        res.setDocumentStatus(DocumentVerificationStatus.PENDING);
        res.setProfileStatus(ProfileStatus.INCOMPLETE);

        when(employeeService.uploadDocumentByEmployee(eq("EMP1001"), any())).thenReturn(res);

        mockMvc.perform(multipart("/api/employee/profile/document").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("id.pdf"))
                .andExpect(jsonPath("$.documentStatus").value("PENDING"))
                .andExpect(jsonPath("$.formCompleted").value(false))
                .andExpect(jsonPath("$.isFormCompleted").value(false));
    }

    @Test
    @WithMockUser(username = "EMP1001", roles = "EMPLOYEE")
    @DisplayName("POST /api/employee/profile/document — 403 Forbidden if profile already SUBMITTED")
    void uploadDocument_whenSubmitted_forbidden() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nvalid".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", pdfBytes);

        when(employeeService.uploadDocumentByEmployee(eq("EMP1001"), any()))
                .thenThrow(new SecurityException("Profile has already been submitted. Documents cannot be modified"));

        mockMvc.perform(multipart("/api/employee/profile/document").file(file))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Profile has already been submitted. Documents cannot be modified"));
    }

    @Test
    @WithMockUser(username = "client01", roles = "USER")
    @DisplayName("GET /api/employee/profile — 403 Forbidden for client role USER")
    void getProfile_asClientUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/employee/profile"))
                .andExpect(status().isForbidden());
    }
}
