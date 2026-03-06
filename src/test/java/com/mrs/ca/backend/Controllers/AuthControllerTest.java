package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import com.mrs.ca.backend.Services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminService adminService;
    @MockitoBean private UserService userService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

    // ===================== Admin Login =====================

    @Test
    @DisplayName("POST /api/auth/admin/login — 200 on valid credentials")
    void adminLogin_success() throws Exception {
        when(adminService.authenticateAdmin("admin", "admin123")).thenReturn(true);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    @DisplayName("POST /api/auth/admin/login — 401 on invalid credentials")
    void adminLogin_failure() throws Exception {
        when(adminService.authenticateAdmin("admin", "wrong")).thenReturn(false);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("POST /api/auth/admin/login — 400 when fields missing")
    void adminLogin_missingFields() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ===================== User Login =====================

    @Test
    @DisplayName("POST /api/auth/user/login — 200 on valid credentials")
    void userLogin_success() throws Exception {
        User user = new User("user01", "pass", "John Doe", "j@m.com", "admin");
        when(userService.authenticateUser("user01", "pass")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01","password":"pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));
    }

    @Test
    @DisplayName("POST /api/auth/user/login — 401 on invalid credentials")
    void userLogin_failure() throws Exception {
        when(userService.authenticateUser("user01", "wrong")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("POST /api/auth/user/login — 400 when fields missing")
    void userLogin_missingFields() throws Exception {
        mockMvc.perform(post("/api/auth/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
