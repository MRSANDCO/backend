package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.DriveLink;
import com.mrs.ca.backend.Services.DriveLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(DriveLinkController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@WithMockUser(roles = "ADMIN")
class DriveLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriveLinkService driveLinkService;

    @MockitoBean
    private MongoMappingContext mongoMappingContext;

    // ===================== POST /api/admin/drive-links =====================

    @Test
    @DisplayName("POST /api/admin/drive-links — 201 on success")
    void saveDriveLink_success() throws Exception {
        DriveLink link = new DriveLink("user01", "2026", "https://url.com", "Title", "Desc", "admin");
        link.setId("link123");

        when(driveLinkService.saveDriveLink(eq("user01"), eq("2026"), eq("https://url.com"), eq("Title"), eq("Desc")))
                .thenReturn(link);

        mockMvc.perform(post("/api/admin/drive-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
                                  "year": "2026",
                                  "driveUrl": "https://url.com",
                                  "title": "Title",
                                  "description": "Desc"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Drive link shared successfully"))
                .andExpect(jsonPath("$.id").value("link123"))
                .andExpect(jsonPath("$.userId").value("user01"));
    }

    @Test
    @DisplayName("POST /api/admin/drive-links — 400 when userId is missing")
    void saveDriveLink_missingUserId() throws Exception {
        mockMvc.perform(post("/api/admin/drive-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "year": "2026",
                                  "driveUrl": "https://url.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("userId is required"));
    }

    // ===================== PUT /api/admin/drive-links/{id} =====================

    @Test
    @DisplayName("PUT /api/admin/drive-links/{id} — 200 on success")
    void updateDriveLink_success() throws Exception {
        DriveLink updated = new DriveLink("user01", "2026", "https://new-url.com", "New Title", "New Desc", "admin");
        updated.setId("link123");

        when(driveLinkService.updateDriveLink(eq("link123"), eq("user01"), eq("2026"), eq("https://new-url.com"), eq("New Title"), eq("New Desc")))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/drive-links/link123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
                                  "year": "2026",
                                  "driveUrl": "https://new-url.com",
                                  "title": "New Title",
                                  "description": "New Desc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Drive link updated successfully"))
                .andExpect(jsonPath("$.id").value("link123"))
                .andExpect(jsonPath("$.userId").value("user01"));
    }

    @Test
    @DisplayName("PUT /api/admin/drive-links/{id} — 400 when service throws IllegalArgumentException")
    void updateDriveLink_invalidInput() throws Exception {
        when(driveLinkService.updateDriveLink(eq("link123"), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Drive link not found"));

        mockMvc.perform(put("/api/admin/drive-links/link123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user01",
                                  "year": "2026",
                                  "driveUrl": "https://new-url.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Drive link not found"));
    }

    // ===================== GET /api/admin/drive-links =====================

    @Test
    @DisplayName("GET /api/admin/drive-links — returns all links")
    void getAllDriveLinks() throws Exception {
        DriveLink link = new DriveLink("user01", "2026", "https://url.com", "Title", "Desc", "admin");
        link.setId("link123");

        when(driveLinkService.getAllDriveLinks(isNull())).thenReturn(List.of(link));

        mockMvc.perform(get("/api/admin/drive-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("link123"));
    }

    // ===================== DELETE /api/admin/drive-links/{id} =====================

    @Test
    @DisplayName("DELETE /api/admin/drive-links/{id} — 200 on success")
    void deleteDriveLink_success() throws Exception {
        doNothing().when(driveLinkService).deleteDriveLink("link123");

        mockMvc.perform(delete("/api/admin/drive-links/link123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Drive link deleted successfully"))
                .andExpect(jsonPath("$.id").value("link123"));
    }

    @Test
    @DisplayName("DELETE /api/admin/drive-links/{id} — 400 on error")
    void deleteDriveLink_error() throws Exception {
        doThrow(new IllegalArgumentException("Drive link not found"))
                .when(driveLinkService).deleteDriveLink("missing");

        mockMvc.perform(delete("/api/admin/drive-links/missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Drive link not found"));
    }
}
