package com.mrs.ca.backend.Integration;

import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Models.EmploymentStatus;
import com.mrs.ca.backend.Models.ProfileStatus;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmployeeProfileAndStatusIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private String getAdminToken() {
        return "Bearer " + jwtUtil.generateToken("admin_test", "admin");
    }

    private String getEmployeeToken(String employeeId) {
        return "Bearer " + jwtUtil.generateToken(employeeId, "employee");
    }

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>%%EOF".getBytes();

    @BeforeEach
    void setup() {
        employeeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("FLOW 1 & FLOW 2: Admin creates employee → Employee logs in → Employee fills profile + uploads Aadhaar → Clicks Submit Profile → Admin views unmasked Aadhaar and PAN")
    void testEmployeeSubmissionAndAdminVisibilityFlow() throws Exception {
        // Step 1: Admin creates employee
        String createEmpJson = """
                {
                    "name": "Rajesh Kumar",
                    "mobileNumber": "9876543210",
                    "email": "rajesh@example.com"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmpJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").exists())
                .andExpect(jsonPath("$.initialPassword").exists())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootNode =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(createResponse);
        String employeeId = rootNode.get("employeeId").asText();
        String initialPassword = rootNode.get("initialPassword").asText();

        // Step 2: Employee logs in via auth endpoint
        mockMvc.perform(post("/api/auth/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"employeeId\":\"%s\",\"password\":\"%s\"}", employeeId, initialPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("employee"))
                .andExpect(jsonPath("$.token").exists());

        String empToken = getEmployeeToken(employeeId);

        // Step 3: Employee uploads Aadhaar document
        MockMultipartFile aadhaarFile = new MockMultipartFile(
                "file",
                "aadhaar_card.pdf",
                "application/pdf",
                VALID_PDF_BYTES
        );

        mockMvc.perform(multipart("/api/employee/profile/document")
                        .file(aadhaarFile)
                        .header("Authorization", empToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentStatus").value("PENDING"));

        // Step 4: Employee fills and submits profile
        String fullAadhaar = "998877665544";
        String fullPan = "ABCDE1234F";

        String submitPayload = String.format("""
                {
                    "name": "Rajesh Kumar",
                    "father_name": "Suresh Kumar",
                    "mobile_number": "9876543210",
                    "aadhaar_number": "%s",
                    "pan_number": "%s",
                    "permanent_address": "Flat 402, Sunshine Heights, Civil Lines, Jaipur",
                    "current_address": "Flat 402, Sunshine Heights, Civil Lines, Jaipur",
                    "date_of_joining": "2025-02-01"
                }
                """, fullAadhaar, fullPan);

        mockMvc.perform(post("/api/employee/profile/submit")
                        .header("Authorization", empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.profileStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.formCompleted").value(true))
                .andExpect(jsonPath("$.profile.aadhaarNumber").value(fullAadhaar))
                .andExpect(jsonPath("$.profile.panNumber").value(fullPan));

        // Step 5: FLOW 2 verification — Authorized Admin retrieves employee profile
        // Complete unmasked Aadhaar and PAN must be visible!
        mockMvc.perform(get("/api/admin/employees/" + employeeId)
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeId))
                .andExpect(jsonPath("$.name").value("Rajesh Kumar"))
                .andExpect(jsonPath("$.fatherName").value("Suresh Kumar"))
                .andExpect(jsonPath("$.mobileNumber").value("9876543210"))
                .andExpect(jsonPath("$.aadhaarNumber").value(fullAadhaar)) // UNMASKED
                .andExpect(jsonPath("$.panNumber").value(fullPan))         // UNMASKED
                .andExpect(jsonPath("$.profileStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.formCompleted").value(true))
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.profileSubmittedAt").exists());

        // Step 6: Admin verifies profile
        mockMvc.perform(post("/api/admin/employees/" + employeeId + "/profile/verify")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("VERIFIED"));

        // Verify state in DB
        var savedEmp = employeeRepository.findByEmployeeId(employeeId).orElseThrow();
        assertThat(savedEmp.getProfileStatus()).isEqualTo(ProfileStatus.VERIFIED);
        assertThat(savedEmp.getVerifiedAt()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("FLOW 3 & FLOW 4: Admin marks ACTIVE employee as EX_EMPLOYEE → separates lists → Idempotent on second call")
    void testExEmployeeLifecycleAndIdempotency() throws Exception {
        // Create employee 1
        String createEmp1 = mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Employee One\",\"mobileNumber\":\"9111111111\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String emp1Id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(createEmp1).get("employeeId").asText();

        // Create employee 2
        String createEmp2 = mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Employee Two\",\"mobileNumber\":\"9222222222\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String emp2Id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(createEmp2).get("employeeId").asText();

        // Initially both are in Active list
        mockMvc.perform(get("/api/admin/employees/active")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Ex-employee list is initially empty
        mockMvc.perform(get("/api/admin/employees/ex-employees")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Admin marks Employee 1 as EX_EMPLOYEE via shorthand endpoint
        mockMvc.perform(post("/api/admin/employees/" + emp1Id + "/mark-ex-employee")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("EX_EMPLOYEE"));

        // FLOW 3: Employee 1 is no longer in Active list
        mockMvc.perform(get("/api/admin/employees/active")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeId").value(emp2Id));

        // FLOW 3: Employee 1 is in Ex-Employee list
        mockMvc.perform(get("/api/admin/employees/ex-employees")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeId").value(emp1Id));

        // Employee 1 data remains preserved in database
        var emp1 = employeeRepository.findByEmployeeId(emp1Id).orElseThrow();
        assertThat(emp1.getName()).isEqualTo("Employee One");
        assertThat(emp1.getEmploymentStatus()).isEqualTo(EmploymentStatus.EX_EMPLOYEE);

        // FLOW 4: Admin marks Employee 1 as EX_EMPLOYEE AGAIN (idempotency check)
        mockMvc.perform(request(org.springframework.http.HttpMethod.PATCH, "/api/admin/employees/" + emp1Id + "/employment-status")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employmentStatus\":\"EX_EMPLOYEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("EX_EMPLOYEE"));

        // Lists remain consistent
        mockMvc.perform(get("/api/admin/employees/active")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/admin/employees/ex-employees")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
