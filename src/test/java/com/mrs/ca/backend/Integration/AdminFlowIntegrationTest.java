package com.mrs.ca.backend.Integration;

import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test that boots the full Spring context with embedded MongoDB.
 * Tests the admin user-management flow end-to-end through HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Full flow: create user → list users → verify in DB")
    void createAndListUser() throws Exception {
        // 1. Create a user via the admin API
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "integ_user",
                                    "password": "secret",
                                    "fullName": "Integration Test User",
                                    "email": "integ@test.com",
                                    "phone": "9999"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("integ_user"));

        // 2. Verify user exists in DB
        assertThat(userRepository.findByUserId("integ_user")).isPresent();

        // 3. List all users via API
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("integ_user"))
                .andExpect(jsonPath("$[0].fullName").value("Integration Test User"));
    }

    @Test
    @Order(2)
    @DisplayName("User login flow: create user → authenticate → verify")
    void userLoginFlow() throws Exception {
        // Create user first
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"login_user","password":"mypass","fullName":"Login Tester","email":"login@t.com"}
                                """))
                .andExpect(status().isCreated());

        // Auth with correct credentials
        mockMvc.perform(post("/api/auth/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"login_user","password":"mypass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.fullName").value("Login Tester"));

        // Auth with wrong password
        mockMvc.perform(post("/api/auth/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"login_user","password":"wrongpass"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("Admin login flow")
    void adminLoginFlow() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    @Order(4)
    @DisplayName("User profile endpoint returns correct data")
    void userProfile() throws Exception {
        // Create user
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"profile_user","password":"pass","fullName":"Profile Test","email":"p@t.com","phone":"1111"}
                                """))
                .andExpect(status().isCreated());

        // Fetch profile
        mockMvc.perform(get("/api/user/profile_user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("profile_user"))
                .andExpect(jsonPath("$.fullName").value("Profile Test"))
                .andExpect(jsonPath("$.phone").value("1111"));
    }

    @Test
    @Order(5)
    @DisplayName("Duplicate user creation returns 400")
    void duplicateUser() throws Exception {
        String body = """
                {"userId":"dup_user","password":"pass","fullName":"Dup","email":"d@t.com"}
                """;

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second attempt should fail
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User ID 'dup_user' already exists"));
    }
}
