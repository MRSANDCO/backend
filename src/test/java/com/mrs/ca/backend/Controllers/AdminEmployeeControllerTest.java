package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.DocumentVerificationStatus;
import com.mrs.ca.backend.Models.ProfileStatus;
import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.dto.CreateEmployeeRequest;
import com.mrs.ca.backend.dto.CreateEmployeeResponse;
import com.mrs.ca.backend.dto.EmployeeProfileResponse;
import com.mrs.ca.backend.dto.UpdateProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEmployeeController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class AdminEmployeeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EmployeeService employeeService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

    // =========================================================================
    // Admin Operations (Role ADMIN)
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/employees — 201 Created on valid input")
    void createEmployee_success() throws Exception {
        CreateEmployeeResponse response = new CreateEmployeeResponse(
                "EMP1001", "Alice", "9876543210", "tempPass123", ProfileStatus.INCOMPLETE, "Created"
        );
        when(employeeService.createEmployee(any(CreateEmployeeRequest.class), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","mobileNumber":"9876543210"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value("EMP1001"))
                .andExpect(jsonPath("$.initialPassword").value("tempPass123"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/employees — 200 OK")
    void getAllEmployees_success() throws Exception {
        EmployeeProfileResponse emp = new EmployeeProfileResponse();
        emp.setEmployeeId("EMP1001");
        emp.setName("Alice");

        when(employeeService.getAllEmployees(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(emp)));

        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeId").value("EMP1001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/employees/{employeeId} — 200 OK")
    void getEmployeeById_success() throws Exception {
        EmployeeProfileResponse emp = new EmployeeProfileResponse();
        emp.setEmployeeId("EMP1001");
        emp.setName("Alice");

        when(employeeService.getEmployeeByIdForAdmin("EMP1001")).thenReturn(emp);

        mockMvc.perform(get("/api/admin/employees/EMP1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("EMP1001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/admin/employees/{employeeId} — 200 OK on admin edit")
    void updateEmployee_success() throws Exception {
        EmployeeProfileResponse emp = new EmployeeProfileResponse();
        emp.setEmployeeId("EMP1001");
        emp.setName("Alice Updated");

        when(employeeService.updateEmployeeByAdmin(eq("EMP1001"), any(UpdateProfileRequest.class))).thenReturn(emp);

        mockMvc.perform(put("/api/admin/employees/EMP1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.name").value("Alice Updated"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/employees/{employeeId}/reset-password — 200 OK")
    void resetPassword_success() throws Exception {
        when(employeeService.resetEmployeePassword(eq("EMP1001"), any())).thenReturn("NewSecretPassword!");

        mockMvc.perform(post("/api/admin/employees/EMP1001/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newPassword").value("NewSecretPassword!"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/employees/{employeeId}/document/verify — 200 OK")
    void verifyDocument_success() throws Exception {
        EmployeeProfileResponse emp = new EmployeeProfileResponse();
        emp.setEmployeeId("EMP1001");
        emp.setDocumentStatus(DocumentVerificationStatus.VERIFIED);

        when(employeeService.verifyDocument("EMP1001")).thenReturn(emp);

        mockMvc.perform(post("/api/admin/employees/EMP1001/document/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStatus").value("VERIFIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/employees/{employeeId}/document/reject — 200 OK")
    void rejectDocument_success() throws Exception {
        EmployeeProfileResponse emp = new EmployeeProfileResponse();
        emp.setEmployeeId("EMP1001");
        emp.setDocumentStatus(DocumentVerificationStatus.REJECTED);
        emp.setDocumentRejectionReason("Unclear scan");

        when(employeeService.rejectDocument(eq("EMP1001"), anyString())).thenReturn(emp);

        mockMvc.perform(post("/api/admin/employees/EMP1001/document/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Unclear scan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Unclear scan"));
    }

    // =========================================================================
    // Access Control (Non-Admin Access Denied)
    // =========================================================================

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("GET /api/admin/employees — 403 Forbidden for role EMPLOYEE")
    void getEmployees_asEmployee_forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/admin/employees — 403 Forbidden for role USER")
    void createEmployee_asUser_forbidden() throws Exception {
        mockMvc.perform(post("/api/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","mobileNumber":"9876543210"}
                                """))
                .andExpect(status().isForbidden());
    }
}
