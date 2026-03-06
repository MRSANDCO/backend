package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    // ===================== GET /api/user/{userId}/documents =====================

    @Test
    @DisplayName("GET /api/user/{userId}/documents — 200 with documents")
    void getMyDocuments_success() throws Exception {
        when(userService.getMyDocuments("user01")).thenReturn(List.of());

        mockMvc.perform(get("/api/user/user01/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/user/{userId}/documents — 400 when user not found")
    void getMyDocuments_userNotFound() throws Exception {
        when(userService.getMyDocuments("ghost"))
                .thenThrow(new IllegalArgumentException("User 'ghost' not found"));

        mockMvc.perform(get("/api/user/ghost/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User 'ghost' not found"));
    }

    // ===================== GET /api/user/{userId}/documents/{docId} =====================

    @Test
    @DisplayName("GET /api/user/{userId}/documents/{docId} — 200 on success")
    void getDocument_success() throws Exception {
        User owner = new User("user01", "p", "John", "j@m.com", "admin");
        owner.setId("uid1");
        Document doc = new Document("Tax Doc", "Desc", "f.pdf", "/p", "pdf", 1L, "tax", "admin", owner);
        doc.setId("doc1");
        when(userService.getDocumentById("doc1", "user01")).thenReturn(doc);

        mockMvc.perform(get("/api/user/user01/documents/doc1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tax Doc"));
    }

    @Test
    @DisplayName("GET /api/user/{userId}/documents/{docId} — 403 on access denied")
    void getDocument_accessDenied() throws Exception {
        when(userService.getDocumentById("doc1", "user01"))
                .thenThrow(new SecurityException("Access denied: document does not belong to this user"));

        mockMvc.perform(get("/api/user/user01/documents/doc1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    // ===================== GET /api/user/{userId}/profile =====================

    @Test
    @DisplayName("GET /api/user/{userId}/profile — 200 with user data")
    void getProfile_success() throws Exception {
        User user = new User("user01", "p", "John Doe", "j@m.com", "admin");
        user.setId("uid1");
        when(userService.getProfile("user01")).thenReturn(user);

        mockMvc.perform(get("/api/user/user01/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));
    }

    @Test
    @DisplayName("GET /api/user/{userId}/profile — 400 when user not found")
    void getProfile_notFound() throws Exception {
        when(userService.getProfile("ghost"))
                .thenThrow(new IllegalArgumentException("User 'ghost' not found"));

        mockMvc.perform(get("/api/user/ghost/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User 'ghost' not found"));
    }
}
