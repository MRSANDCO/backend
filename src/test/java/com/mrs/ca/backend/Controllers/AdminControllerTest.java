package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtAuthFilter;
import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Config.SecurityConfig;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import com.mrs.ca.backend.Services.QueryService;
import com.mrs.ca.backend.Services.WhatsAppService;
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

import com.mrs.ca.backend.Services.FounderService;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@WithMockUser(roles = "ADMIN")
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminService adminService;
    @MockitoBean private QueryService queryService;
    @MockitoBean private WhatsAppService whatsAppService;
    @MockitoBean private FounderService founderService;
    @MockitoBean private MongoMappingContext mongoMappingContext;

    // ===================== POST /api/admin/users =====================

    @Test
    @DisplayName("POST /api/admin/users — 201 on success")
    void createUser_success() throws Exception {
        User user = new User("user01", "pass", "John", "j@m.com", "admin");
        user.setId("abc123");
        user.setPhone("12345");
        when(adminService.createUser(eq("user01"), eq("pass"), eq("John"), eq("j@m.com"), eq("12345")))
                .thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user01","password":"pass","fullName":"John","email":"j@m.com","phone":"12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.fullName").value("John"))
                .andExpect(jsonPath("$.email").value("j@m.com"))
                .andExpect(jsonPath("$.phone").value("12345"))
                .andExpect(jsonPath("$.user.userId").value("user01"));
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

    @Test
    @DisplayName("POST /api/admin/users — 201 on COMPANY creation with formation date")
    void createUser_companySuccess() throws Exception {
        User user = new User("comp01", "pass", "Director John", "director@alpha.com", "admin");
        user.setId("comp123");
        user.setPhone("9876543210");
        user.setClientType(ClientType.COMPANY);
        user.setCompanyName("Alpha Corp Ltd");
        user.setFormationDate(java.time.LocalDate.of(2019, 3, 15));
        user.setAnniversaryAutomationEnabled(true);
        user.setWhatsappConsentGiven(true);

        when(adminService.createUser(eq("comp01"), eq("pass"), eq("Director John"),
                eq("director@alpha.com"), eq("9876543210"), eq(ClientType.COMPANY),
                eq("Alpha Corp Ltd"), isNull(), eq(java.time.LocalDate.of(2019, 3, 15)),
                eq(true), eq(true)))
                .thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "comp01",
                                    "password": "pass",
                                    "fullName": "Director John",
                                    "email": "director@alpha.com",
                                    "phone": "9876543210",
                                    "clientType": "COMPANY",
                                    "companyName": "Alpha Corp Ltd",
                                    "formationDate": "2019-03-15",
                                    "anniversaryAutomationEnabled": "true",
                                    "whatsappConsentGiven": "true"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("comp01"))
                .andExpect(jsonPath("$.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Alpha Corp Ltd"))
                .andExpect(jsonPath("$.formationDate").value("2019-03-15"));
    }

    @Test
    @DisplayName("POST /api/admin/users — 201 on INDIVIDUAL creation without company fields")
    void createUser_individualSuccess() throws Exception {
        User user = new User("ind01", "pass", "Individual John", "john@ind.com", "admin");
        user.setId("ind123");
        user.setPhone("9876543211");
        user.setClientType(ClientType.INDIVIDUAL);
        user.setAnniversaryAutomationEnabled(false);

        when(adminService.createUser(eq("ind01"), eq("pass"), eq("Individual John"),
                eq("john@ind.com"), eq("9876543211"), eq(ClientType.INDIVIDUAL),
                isNull(), isNull(), isNull(), eq(false), eq(false)))
                .thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "ind01",
                                    "password": "pass",
                                    "fullName": "Individual John",
                                    "email": "john@ind.com",
                                    "phone": "9876543211",
                                    "clientType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("ind01"))
                .andExpect(jsonPath("$.clientType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.formationDate").value(""));
    }

    @Test
    @DisplayName("POST /api/admin/users — 201 on INDIVIDUAL creation with dateOfBirth")
    void createUser_individualWithDateOfBirth() throws Exception {
        User user = new User("ind_dob1", "pass", "Individual John", "john@ind.com", "admin");
        user.setId("ind_dob_id");
        user.setPhone("9876543211");
        user.setClientType(ClientType.INDIVIDUAL);
        user.setDateOfBirth(java.time.LocalDate.of(1990, 7, 20));
        user.setBirthdayGreetingEnabled(true);

        when(adminService.createUser(eq("ind_dob1"), eq("pass"), eq("Individual John"),
                eq("john@ind.com"), eq("9876543211"), eq(ClientType.INDIVIDUAL),
                isNull(), isNull(), isNull(),
                eq(java.time.LocalDate.of(1990, 7, 20)),
                isNull(), eq(true), isNull()))
                .thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "ind_dob1",
                                    "password": "pass",
                                    "fullName": "Individual John",
                                    "email": "john@ind.com",
                                    "phone": "9876543211",
                                    "clientType": "INDIVIDUAL",
                                    "dateOfBirth": "1990-07-20",
                                    "birthdayGreetingEnabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("ind_dob1"))
                .andExpect(jsonPath("$.clientType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-07-20"))
                .andExpect(jsonPath("$.birthdayGreetingEnabled").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/users — 400 when invalid clientType provided")
    void createUser_invalidClientType() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "user_bad_type",
                                    "password": "pass",
                                    "clientType": "NOT_A_REAL_TYPE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid client type: 'NOT_A_REAL_TYPE'. Allowed values: COMPANY, INDIVIDUAL"));
    }

    @Test
    @DisplayName("POST /api/admin/users — 400 when enabling automation for INDIVIDUAL client")
    void createUser_rejectAutomationForIndividual() throws Exception {
        when(adminService.createUser(any(), any(), any(), any(), any(), eq(ClientType.INDIVIDUAL), any(), any(), any(), eq(true), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Anniversary automation cannot be enabled for INDIVIDUAL clients"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "ind_auto",
                                    "password": "pass",
                                    "clientType": "INDIVIDUAL",
                                    "anniversaryAutomationEnabled": "true"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Anniversary automation cannot be enabled for INDIVIDUAL clients"));
    }

    // ===================== PUT /api/admin/users/{userId} =====================

    @Test
    @DisplayName("PUT /api/admin/users/{userId} — 200 on success")
    void updateUser_success() throws Exception {
        User updated = new User("user01", "pass", "John Updated", "john_upd@m.com", "admin");
        updated.setId("abc123");
        updated.setPhone("99999");
        when(adminService.updateUser(eq("user01"), eq("John Updated"), eq("john_upd@m.com"), eq("99999")))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/users/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"John Updated","email":"john_upd@m.com","phone":"99999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.fullName").value("John Updated"))
                .andExpect(jsonPath("$.email").value("john_upd@m.com"))
                .andExpect(jsonPath("$.phone").value("99999"))
                .andExpect(jsonPath("$.user.userId").value("user01"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{userId} — 400 when user not found")
    void updateUser_notFound() throws Exception {
        when(adminService.updateUser(eq("ghost"), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("User 'ghost' not found"));

        mockMvc.perform(put("/api/admin/users/ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ghost"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User 'ghost' not found"));
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

    // ===================== GET /api/admin/users/{userId} =====================

    @Test
    @DisplayName("GET /api/admin/users/{userId} — 200 on success")
    void getUser_success() throws Exception {
        User u = new User("user01", "p", "John", "j@m.com", "admin");
        u.setId("id1");
        u.setClientType(ClientType.COMPANY);
        u.setCompanyName("Alpha Corp");
        u.setFormationDate(java.time.LocalDate.of(2018, 5, 10));
        when(adminService.getUser("user01")).thenReturn(u);

        mockMvc.perform(get("/api/admin/users/user01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Alpha Corp"))
                .andExpect(jsonPath("$.formationDate").value("2018-05-10"));
    }

    @Test
    @DisplayName("GET /api/admin/users/{userId} — 404 when not found")
    void getUser_notFound() throws Exception {
        when(adminService.getUser("ghost")).thenThrow(new IllegalArgumentException("User 'ghost' not found"));

        mockMvc.perform(get("/api/admin/users/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User 'ghost' not found"));
    }

    @Test
    @DisplayName("POST /api/admin/users — 400 on malformed formationDate")
    void createUser_invalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "user_bad_date",
                                    "password": "pass",
                                    "formationDate": "not-a-date"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid formationDate format. Expected YYYY-MM-DD: 'not-a-date'"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{userId} — 200 on COMPANY update with formation date")
    void updateUser_companyUpdate() throws Exception {
        User updated = new User("comp01", "pass", "Director John", "dir@c.com", "admin");
        updated.setId("id1");
        updated.setClientType(ClientType.COMPANY);
        updated.setCompanyName("Acme Holdings");
        updated.setFormationDate(java.time.LocalDate.of(2020, 1, 15));

        when(adminService.updateUser(eq("comp01"), eq("Director John"), eq("dir@c.com"), isNull(),
                eq(ClientType.COMPANY), eq("Acme Holdings"), isNull(),
                eq(java.time.LocalDate.of(2020, 1, 15)), isNull(), isNull()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/users/comp01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Director John",
                                    "email": "dir@c.com",
                                    "clientType": "COMPANY",
                                    "companyName": "Acme Holdings",
                                    "formationDate": "2020-01-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Acme Holdings"))
                .andExpect(jsonPath("$.formationDate").value("2020-01-15"));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{userId} — 200 on INDIVIDUAL update")
    void updateUser_individualUpdate() throws Exception {
        User updated = new User("ind01", "pass", "Individual Person", "ind@c.com", "admin");
        updated.setId("id2");
        updated.setClientType(ClientType.INDIVIDUAL);

        when(adminService.updateUser(eq("ind01"), eq("Individual Person"), eq("ind@c.com"), isNull(),
                eq(ClientType.INDIVIDUAL), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/users/ind01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Individual Person",
                                    "email": "ind@c.com",
                                    "clientType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.formationDate").value(""));
    }

    @Test
    @DisplayName("PUT /api/admin/users/{userId} — 400 on malformed formationDate")
    void updateUser_invalidDateFormat() throws Exception {
        mockMvc.perform(put("/api/admin/users/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "formationDate": "15-01-2020"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid formationDate format. Expected YYYY-MM-DD: '15-01-2020'"));
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

    // ===================== GET /api/admin/queries/{queryId}/conversation =====================

    @Test
    @DisplayName("GET /api/admin/queries/{queryId}/conversation — 200 on success")
    void getQueryConversation_success() throws Exception {
        com.mrs.ca.backend.Models.Query query = new com.mrs.ca.backend.Models.Query();
        query.setId("q123");
        query.setSubject("Document Query");

        com.mrs.ca.backend.dto.QueryConversationDto conversation =
                new com.mrs.ca.backend.dto.QueryConversationDto(query, List.of());

        when(queryService.getQueryConversation("q123", null, true)).thenReturn(conversation);

        mockMvc.perform(get("/api/admin/queries/q123/conversation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.id").value("q123"))
                .andExpect(jsonPath("$.responses").isArray());
    }

    // ===================== POST /api/admin/queries/{queryId}/responses =====================

    @Test
    @DisplayName("POST /api/admin/queries/{queryId}/responses (JSON) — 201 on success")
    void submitAdminJsonResponse_success() throws Exception {
        com.mrs.ca.backend.Models.QueryResponse response = new com.mrs.ca.backend.Models.QueryResponse(
                "q123", "user01", "admin", "Admin (admin)", null,
                com.mrs.ca.backend.Models.QueryResponse.SenderRole.ADMIN, "Acknowledged."
        );
        response.setId("resp_admin_1");

        when(queryService.addAdminResponse(eq("q123"), any(), eq("Acknowledged."), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/admin/queries/q123/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Acknowledged.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response.message").value("Acknowledged."))
                .andExpect(jsonPath("$.response.senderRole").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/admin/queries/{queryId}/responses (Multipart with Excel) — 201 on success")
    void submitAdminMultipartResponse_success() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "computation.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "excel data".getBytes()
        );

        com.mrs.ca.backend.Models.QueryResponse response = new com.mrs.ca.backend.Models.QueryResponse(
                "q123", "user01", "admin", "Admin (admin)", null,
                com.mrs.ca.backend.Models.QueryResponse.SenderRole.ADMIN, "Here is the calculation.",
                "grid_admin_1", "computation.xlsx", 200L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        response.setId("resp_admin_2");

        when(queryService.addAdminResponse(eq("q123"), any(), eq("Here is the calculation."), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/admin/queries/q123/responses")
                        .file(file)
                        .param("message", "Here is the calculation."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response.fileName").value("computation.xlsx"));
    }

    // ===================== GET /api/admin/queries/{queryId}/responses/{respId}/download =====================

    @Test
    @DisplayName("GET /api/admin/queries/{queryId}/responses/{respId}/download — 200 on success")
    void downloadAdminResponseAttachment_success() throws Exception {
        doNothing().when(queryService).streamResponseFileForAdmin(eq("q123"), eq("resp_admin_1"), any());

        mockMvc.perform(get("/api/admin/queries/q123/responses/resp_admin_1/download"))
                .andExpect(status().isOk());

        verify(queryService).streamResponseFileForAdmin(eq("q123"), eq("resp_admin_1"), any());
    }

    // ===================== PUT /api/admin/queries/{queryId}/status =====================

    @Test
    @DisplayName("PUT /api/admin/queries/{queryId}/status — 200 on success")
    void updateQueryStatus_success() throws Exception {
        com.mrs.ca.backend.Models.Query query = new com.mrs.ca.backend.Models.Query();
        query.setId("q123");
        query.setStatus(com.mrs.ca.backend.Models.Query.QueryStatus.RESOLVED);

        when(queryService.updateQueryStatus("q123", com.mrs.ca.backend.Models.Query.QueryStatus.RESOLVED))
                .thenReturn(query);

        mockMvc.perform(put("/api/admin/queries/q123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("q123"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    // ===================== GET /api/admin/companies =====================

    @Test
    @DisplayName("GET /api/admin/companies — 200 on success returning eligible companies")
    void getEligibleCompanies_success() throws Exception {
        com.mrs.ca.backend.dto.CompanyDropdownDto dto = new com.mrs.ca.backend.dto.CompanyDropdownDto(
                "comp1", "Acme Corp", "Alice", "Alice Manager",
                "9876543210", "acme@test.com", java.time.LocalDate.of(2020, 1, 1),
                true, com.mrs.ca.backend.Models.ClientType.COMPANY, true, true, 2L
        );
        when(founderService.getEligibleCompanies()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value("comp1"))
                .andExpect(jsonPath("$[0].companyName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].founderCount").value(2));
    }
}
