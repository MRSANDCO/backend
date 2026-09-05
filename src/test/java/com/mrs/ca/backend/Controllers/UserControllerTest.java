package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.UserService;
import com.mrs.ca.backend.Services.QueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@WithMockUser(username = "user01", roles = "USER")
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private QueryService queryService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

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
        when(userService.getMyDocuments("user01"))
                .thenThrow(new IllegalArgumentException("User 'user01' not found"));

        mockMvc.perform(get("/api/user/user01/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User 'user01' not found"));
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
        when(userService.getProfile("user01"))
                .thenThrow(new IllegalArgumentException("User 'user01' not found"));

        mockMvc.perform(get("/api/user/user01/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User 'user01' not found"));
    }

    // ===================== POST /api/user/{userId}/queries/{queryId}/responses =====================

    @Test
    @DisplayName("POST /api/user/{userId}/queries/{queryId}/responses (JSON) — 201 on success")
    void submitJsonResponse_success() throws Exception {
        com.mrs.ca.backend.Models.QueryResponse response = new com.mrs.ca.backend.Models.QueryResponse(
                "q123", "user01", "user01", "John Doe", "j@m.com",
                com.mrs.ca.backend.Models.QueryResponse.SenderRole.CLIENT, "Here is my reply."
        );
        response.setId("resp_1");

        when(queryService.addClientResponse(eq("q123"), eq("user01"), eq("Here is my reply."), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/user/user01/queries/q123/responses")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Here is my reply.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response.message").value("Here is my reply."))
                .andExpect(jsonPath("$.response.senderRole").value("CLIENT"));
    }

    @Test
    @DisplayName("POST /api/user/{userId}/queries/{queryId}/responses (Multipart with Excel) — 201 on success")
    void submitMultipartResponse_success() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "excel content".getBytes()
        );

        com.mrs.ca.backend.Models.QueryResponse response = new com.mrs.ca.backend.Models.QueryResponse(
                "q123", "user01", "user01", "John Doe", "j@m.com",
                com.mrs.ca.backend.Models.QueryResponse.SenderRole.CLIENT, "Attached is the spreadsheet.",
                "grid_1", "report.xlsx", 100L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        response.setId("resp_2");

        when(queryService.addClientResponse(eq("q123"), eq("user01"), eq("Attached is the spreadsheet."), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/user/user01/queries/q123/responses")
                        .file(file)
                        .param("message", "Attached is the spreadsheet."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response.fileName").value("report.xlsx"));
    }

    @Test
    @DisplayName("POST /api/user/{userId}/queries/{queryId}/responses — 400 on empty message")
    void submitResponse_emptyMessage() throws Exception {
        mockMvc.perform(post("/api/user/user01/queries/q123/responses")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));
    }

    @Test
    @DisplayName("POST /api/user/{userId}/queries/{queryId}/responses — 403 when user does not match path")
    void submitResponse_forbiddenForOtherUser() throws Exception {
        mockMvc.perform(post("/api/user/otherUser/queries/q123/responses")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Sneaky reply\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    // ===================== GET /api/user/{userId}/queries/{queryId}/responses/{respId}/download =====================

    @Test
    @DisplayName("GET /api/user/{userId}/queries/{queryId}/responses/{respId}/download — 200 on success")
    void downloadResponseAttachment_success() throws Exception {
        doNothing().when(queryService).streamResponseFile(eq("q123"), eq("resp_1"), eq("user01"), any());

        mockMvc.perform(get("/api/user/user01/queries/q123/responses/resp_1/download"))
                .andExpect(status().isOk());

        verify(queryService).streamResponseFile(eq("q123"), eq("resp_1"), eq("user01"), any());
    }

    // ===================== GET /api/user/{userId}/queries/{queryId}/conversation =====================

    @Test
    @DisplayName("GET /api/user/{userId}/queries/{queryId}/conversation — 200 on success")
    void getConversation_success() throws Exception {
        com.mrs.ca.backend.Models.Query query = new com.mrs.ca.backend.Models.Query();
        query.setId("q123");
        query.setSubject("Test Query");

        com.mrs.ca.backend.dto.QueryConversationDto conversation =
                new com.mrs.ca.backend.dto.QueryConversationDto(query, List.of());

        when(queryService.getQueryConversation("q123", "user01", false)).thenReturn(conversation);

        mockMvc.perform(get("/api/user/user01/queries/q123/conversation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.id").value("q123"))
                .andExpect(jsonPath("$.responses").isArray());

        verify(queryService).markSeen("q123", "user01");
    }
}
