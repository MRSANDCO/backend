package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@WithMockUser(roles = "ADMIN")
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminService adminService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

    // ===================== POST /api/admin/users =====================

    @Test
    @DisplayName("POST /api/admin/users — 201 on success")
    void createUser_success() throws Exception {
        User user = new User("user01", "pass", "John", "j@m.com", "admin");
        user.setId("abc123");
        when(adminService.createUser(eq("user01"), eq("pass"), eq("John"), eq("j@m.com"), eq("12345")))
                .thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01","password":"pass","fullName":"John","email":"j@m.com","phone":"12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.userId").value("user01"));
    }

    @Test
    @DisplayName("POST /api/admin/users — 400 when userId missing")
    void createUser_missingFields() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"pass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/admin/users — 400 on duplicate userId")
    void createUser_duplicate() throws Exception {
        when(adminService.createUser(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("User ID 'user01' already exists"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01","password":"pass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User ID 'user01' already exists"));
    }

    // ===================== GET /api/admin/users =====================

    @Test
    @DisplayName("GET /api/admin/users — returns user list")
    void getAllUsers() throws Exception {
        User u = new User("user01", "p", "John", "j@m.com", "admin");
        u.setId("id1");
        when(adminService.getAllUsers()).thenReturn(List.of(u));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user01"));
    }

    // ===================== POST /api/admin/users/{userId}/documents =====================

    @Test
    @DisplayName("POST /api/admin/users/{userId}/documents — 201 on success")
    void uploadDocument_success() throws Exception {
        Document doc = new Document("Tax Return", "Desc", "tax.pdf", null, "application/pdf", 1024L, "tax", "admin", null);
        doc.setId("docId1");
        doc.setGridFsId("someObjectId");

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "tax.pdf", "application/pdf", "PDF content".getBytes());

        when(adminService.uploadDocument(any(), eq("Tax Return"), eq("Desc"), eq("tax"), eq("user01")))
                .thenReturn(doc);

        mockMvc.perform(multipart("/api/admin/users/user01/documents")
                        .file(mockFile)
                        .param("title", "Tax Return")
                        .param("description", "Desc")
                        .param("category", "tax"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Document uploaded successfully"))
                .andExpect(jsonPath("$.documentId").value("docId1"))
                .andExpect(jsonPath("$.fileName").value("tax.pdf"));
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/documents — 400 when file empty")
    void uploadDocument_emptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/admin/users/user01/documents")
                        .file(emptyFile)
                        .param("title", "title"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is required"));
    }

    // ===================== GET /api/admin/documents =====================

    @Test
    @DisplayName("GET /api/admin/documents — returns all documents")
    void getDocuments_all() throws Exception {
        when(adminService.getDocuments(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ===================== DELETE /api/admin/documents/{id} =====================

    @Test
    @DisplayName("DELETE /api/admin/documents/{id} — 200 on success")
    void deleteDocument_success() throws Exception {
        doNothing().when(adminService).deleteDocument("docId1");

        mockMvc.perform(delete("/api/admin/documents/docId1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted"))
                .andExpect(jsonPath("$.documentId").value("docId1"));
    }

    @Test
    @DisplayName("DELETE /api/admin/documents/{id} — 400 when not found")
    void deleteDocument_notFound() throws Exception {
        doThrow(new IllegalArgumentException("Document not found"))
                .when(adminService).deleteDocument("missing");

        mockMvc.perform(delete("/api/admin/documents/missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document not found"));
    }
}
