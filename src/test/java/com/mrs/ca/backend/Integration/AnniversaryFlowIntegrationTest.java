package com.mrs.ca.backend.Integration;

import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.WhatsAppMessageResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the Automated Company Formation Anniversary Greeting API.
 * Boots the full Spring Boot application with embedded MongoDB and tests all endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnniversaryFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AnniversaryNotificationLogRepository logRepository;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private RestTemplate restTemplate;

    private String getAdminToken() {
        return "Bearer " + jwtUtil.generateToken("admin", "admin");
    }

    private String getClientToken(String clientId) {
        return "Bearer " + jwtUtil.generateToken(clientId, "user");
    }

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        logRepository.deleteAll();

        // Configure default mock response for Meta WhatsApp Cloud API template dispatches
        WhatsAppMessageResponse mockResponse = new WhatsAppMessageResponse();
        WhatsAppMessageResponse.Message msg = new WhatsAppMessageResponse.Message();
        msg.setId("wamid.integ_test_msg_999");
        mockResponse.setMessages(List.of(msg));

        when(restTemplate.postForEntity(anyString(), any(), eq(WhatsAppMessageResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));
    }

    @Test
    @Order(1)
    @DisplayName("Security: Unauthenticated and non-admin requests to anniversary endpoints are rejected")
    void testSecurityEnforcement() throws Exception {
        // 1. Unauthenticated request -> 401 or 403
        mockMvc.perform(get("/api/admin/anniversary/today"))
                .andExpect(status().isForbidden());

        // 2. Client role (non-admin) -> 403 Forbidden
        mockMvc.perform(get("/api/admin/anniversary/today")
                        .header("Authorization", getClientToken("some_client")))
                .andExpect(status().isForbidden());

        // 3. Admin role -> 200 OK
        mockMvc.perform(get("/api/admin/anniversary/today")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("Admin updates client formation date, automation preference, and WhatsApp consent")
    void testClientSettingsUpdate() throws Exception {
        // Create client record in MongoDB
        User client = new User("client_alpha", "pass", "John Doe", "alpha@test.com", "admin");
        client.setPhone("9876543210");
        userRepository.save(client);

        // Update settings via admin API
        String settingsJson = """
                {
                    "clientType": "COMPANY",
                    "formationDate": "2018-10-10",
                    "companyName": "Alpha Corp Ltd",
                    "contactPersonName": "John Doe",
                    "anniversaryAutomationEnabled": true,
                    "whatsappConsentGiven": true
                }
                """;

        mockMvc.perform(put("/api/admin/anniversary/clients/client_alpha/settings")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Anniversary settings updated successfully"))
                .andExpect(jsonPath("$.client.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.client.companyName").value("Alpha Corp Ltd"))
                .andExpect(jsonPath("$.client.anniversaryAutomationEnabled").value(true))
                .andExpect(jsonPath("$.client.whatsappConsentGiven").value(true));

        // Verify database persistence
        User persisted = userRepository.findByUserId("client_alpha").orElseThrow();
        assertThat(persisted.getClientType()).isEqualTo(ClientType.COMPANY);
        assertThat(persisted.getFormationDate()).isEqualTo(LocalDate.of(2018, 10, 10));
        assertThat(persisted.getCompanyName()).isEqualTo("Alpha Corp Ltd");
        assertThat(persisted.isAnniversaryAutomationEnabled()).isTrue();
        assertThat(persisted.isWhatsappConsentGiven()).isTrue();
        assertThat(persisted.getWhatsappConsentDate()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("GET /today and GET /upcoming return accurate lists based on formation dates and exclude INDIVIDUAL clients")
    void testTodayAndUpcomingAnniversaries() throws Exception {
        LocalDate today = LocalDate.now();

        // Client 1: Anniversary is today (formed 5 years ago) - COMPANY
        User clientToday = new User("client_today", "pass", "Ramesh", "c1@test.com", "admin");
        clientToday.setClientType(ClientType.COMPANY);
        clientToday.setCompanyName("Today Tech Pvt Ltd");
        clientToday.setPhone("9876543211");
        clientToday.setFormationDate(today.minusYears(5));
        clientToday.setAnniversaryAutomationEnabled(true);
        clientToday.setWhatsappConsentGiven(true);
        userRepository.save(clientToday);

        // Client 2: Anniversary in 3 days - COMPANY
        User clientUpcoming = new User("client_upcoming", "pass", "Suresh", "c2@test.com", "admin");
        clientUpcoming.setClientType(ClientType.COMPANY);
        clientUpcoming.setCompanyName("Upcoming Ventures");
        clientUpcoming.setPhone("9876543212");
        clientUpcoming.setFormationDate(today.plusDays(3).minusYears(3));
        clientUpcoming.setAnniversaryAutomationEnabled(true);
        clientUpcoming.setWhatsappConsentGiven(true);
        userRepository.save(clientUpcoming);

        // Client 3: Individual client whose date matches today - must be EXCLUDED
        User clientIndividual = new User("client_ind_today", "pass", "Anita", "anita@test.com", "admin");
        clientIndividual.setClientType(ClientType.INDIVIDUAL);
        clientIndividual.setPhone("9876543213");
        clientIndividual.setFormationDate(today.minusYears(2));
        userRepository.save(clientIndividual);

        // 1. Verify GET /api/admin/anniversary/today returns only clientToday (excludes clientIndividual)
        mockMvc.perform(get("/api/admin/anniversary/today")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("client_today"))
                .andExpect(jsonPath("$[0].companyName").value("Today Tech Pvt Ltd"))
                .andExpect(jsonPath("$[0].yearsCompleted").value(5))
                .andExpect(jsonPath("$[0].anniversaryToday").value(true));

        // 2. Verify GET /api/admin/anniversary/upcoming?days=7 returns clientUpcoming
        mockMvc.perform(get("/api/admin/anniversary/upcoming?days=7")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("client_upcoming"))
                .andExpect(jsonPath("$[0].companyName").value("Upcoming Ventures"));
    }

    @Test
    @Order(4)
    @DisplayName("End-to-End Send Flow: Scheduler trigger → Meta accepted → Webhook delivered → Webhook read")
    void testEndToEndSendAndWebhookReconciliation() throws Exception {
        LocalDate today = LocalDate.now();

        User client = new User("client_e2e", "pass", "Vikram", "e2e@test.com", "admin");
        client.setClientType(ClientType.COMPANY);
        client.setCompanyName("Zenith Solutions");
        client.setPhone("9876543210");
        client.setFormationDate(today.minusYears(4));
        client.setAnniversaryAutomationEnabled(true);
        client.setWhatsappConsentGiven(true);
        userRepository.save(client);

        // 1. Trigger scheduler run on-demand
        mockMvc.perform(post("/api/admin/anniversary/scheduler/trigger-now")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Anniversary scheduler completed"))
                .andExpect(jsonPath("$.summary.eligibleToday").value(1))
                .andExpect(jsonPath("$.summary.processedSuccessfully").value(1));

        // Verify notification log created with status ACCEPTED and Meta Message ID
        Optional<AnniversaryNotificationLog> logOpt = logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "client_e2e", today.getYear(), AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY);

        assertThat(logOpt).isPresent();
        AnniversaryNotificationLog log = logOpt.get();
        assertThat(log.getStatus()).isEqualTo(AnniversaryNotificationStatus.ACCEPTED);
        assertThat(log.getMetaMessageId()).isEqualTo("wamid.integ_test_msg_999");
        assertThat(log.getTemplateName()).isEqualTo("company_anniversary_greeting");
        assertThat(log.getTemplateLanguage()).isEqualTo("en");

        // 2. Test Idempotency: Triggering scheduler a second time does NOT send a duplicate
        mockMvc.perform(post("/api/admin/anniversary/scheduler/trigger-now")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.processedSuccessfully").value(0));

        // 3. Receive Meta Webhook 'delivered' event
        String deliveredWebhookJson = """
                {
                    "object": "whatsapp_business_account",
                    "entry": [{
                        "id": "12345",
                        "changes": [{
                            "value": {
                                "messaging_product": "whatsapp",
                                "statuses": [{
                                    "id": "wamid.integ_test_msg_999",
                                    "status": "delivered",
                                    "timestamp": 1728556800
                                }]
                            },
                            "field": "messages"
                        }]
                    }]
                }
                """;

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveredWebhookJson))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        // Verify status transitioned to DELIVERED in DB
        AnniversaryNotificationLog afterDelivered = logRepository.findById(log.getId()).orElseThrow();
        assertThat(afterDelivered.getStatus()).isEqualTo(AnniversaryNotificationStatus.DELIVERED);
        assertThat(afterDelivered.getDeliveryAt()).isNotNull();

        // 4. Receive Meta Webhook 'read' event
        String readWebhookJson = """
                {
                    "object": "whatsapp_business_account",
                    "entry": [{
                        "id": "12345",
                        "changes": [{
                            "value": {
                                "messaging_product": "whatsapp",
                                "statuses": [{
                                    "id": "wamid.integ_test_msg_999",
                                    "status": "read",
                                    "timestamp": 1728556900
                                }]
                            },
                            "field": "messages"
                        }]
                    }]
                }
                """;

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readWebhookJson))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        // Verify status transitioned to READ in DB
        AnniversaryNotificationLog afterRead = logRepository.findById(log.getId()).orElseThrow();
        assertThat(afterRead.getStatus()).isEqualTo(AnniversaryNotificationStatus.READ);
        assertThat(afterRead.getReadAt()).isNotNull();

        // 5. Query history endpoint to ensure admin can inspect logs
        mockMvc.perform(get("/api/admin/anniversary/history")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientId").value("client_e2e"))
                .andExpect(jsonPath("$.content[0].status").value("READ"));
    }

    @Test
    @Order(5)
    @DisplayName("Admin Test-Send endpoint sends template to designated number")
    void testAdminTestSendEndpoint() throws Exception {
        String testSendJson = """
                {
                    "recipientPhone": "919876543210",
                    "companyName": "Test Firm Ltd",
                    "contactPersonName": "Test Contact"
                }
                """;

        mockMvc.perform(post("/api/admin/anniversary/test-send")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testSendJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.metaMessageId").value("wamid.integ_test_msg_999"));
    }

    @Test
    @Order(6)
    @DisplayName("Scheduler strictly excludes INDIVIDUAL clients and UNVERIFIED clients even with matching anniversary dates")
    void testIndividualAndUnverifiedClientsExcludedFromScheduler() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Individual client whose formation date matches today
        User individual = new User("ind_user_1", "pass", "Individual User", "ind@test.com", "admin");
        individual.setClientType(ClientType.INDIVIDUAL);
        individual.setPhone("9876543210");
        individual.setFormationDate(today.minusYears(3));
        individual.setAnniversaryAutomationEnabled(false);
        userRepository.save(individual);

        // 2. Unresolved / unverified client (clientType is null) whose formation date matches today
        User unverified = new User("unverified_user_1", "pass", "Unverified User", "unv@test.com", "admin");
        unverified.setClientType(null);
        unverified.setPhone("9876543211");
        unverified.setFormationDate(today.minusYears(2));
        unverified.setAnniversaryAutomationEnabled(true);
        unverified.setWhatsappConsentGiven(true);
        userRepository.save(unverified);

        // Trigger scheduler
        mockMvc.perform(post("/api/admin/anniversary/scheduler/trigger-now")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eligibleToday").value(0))
                .andExpect(jsonPath("$.summary.processedSuccessfully").value(0));

        // Ensure no notification log was created for either
        assertThat(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "ind_user_1", today.getYear(), AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY)).isEmpty();
        assertThat(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "unverified_user_1", today.getYear(), AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY)).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("User API: Create COMPANY with formation date, create INDIVIDUAL without company details, and reject invalid clientType")
    void testUserApi_ClientTypeScenarios() throws Exception {
        // 1. Create COMPANY client with formation date
        String companyJson = """
                {
                    "userId": "comp_api_01",
                    "password": "SecretPassword123!",
                    "fullName": "Director Name",
                    "email": "director@company.com",
                    "phone": "9876543210",
                    "clientType": "COMPANY",
                    "companyName": "Acme Innovations Ltd",
                    "formationDate": "2020-05-15",
                    "anniversaryAutomationEnabled": "true",
                    "whatsappConsentGiven": "true"
                }
                """;
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Acme Innovations Ltd"))
                .andExpect(jsonPath("$.formationDate").value("2020-05-15"));

        // 2. Create INDIVIDUAL client without company name or formation date
        String individualJson = """
                {
                    "userId": "ind_api_01",
                    "password": "SecretPassword123!",
                    "fullName": "Private Individual",
                    "email": "private@individual.com",
                    "phone": "9876543211",
                    "clientType": "INDIVIDUAL"
                }
                """;
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.formationDate").value(""))
                .andExpect(jsonPath("$.anniversaryAutomationEnabled").value(false));

        // 3. Reject invalid client type
        String invalidTypeJson = """
                {
                    "userId": "invalid_type_01",
                    "password": "SecretPassword123!",
                    "fullName": "Invalid Person",
                    "email": "inv@test.com",
                    "phone": "9876543212",
                    "clientType": "PARTNERSHIP_FIRM"
                }
                """;
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid client type: 'PARTNERSHIP_FIRM'. Allowed values: COMPANY, INDIVIDUAL"));
    }

    @Test
    @Order(8)
    @DisplayName("Legacy records: Unverified clients (clientType == null) are safely held, retrievable via GET /users/{userId} and /unverified, and can be resolved to COMPANY or INDIVIDUAL")
    void testLegacyRecords_UnverifiedClientsAndResolution() throws Exception {
        // 1. Create a legacy client directly in the repository with null clientType
        User legacy = new User("legacy_client_01", "pass", "Old Client Name", "old@client.com", "admin");
        legacy.setPhone("9876543210");
        legacy.setClientType(null); // Unclassified
        legacy.setAnniversaryAutomationEnabled(false);
        legacy.setWhatsappConsentGiven(false);
        userRepository.save(legacy);

        // 2. Fetch single client via GET /api/admin/users/{userId}
        mockMvc.perform(get("/api/admin/users/legacy_client_01")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("legacy_client_01"))
                .andExpect(jsonPath("$.clientType").isEmpty())
                .andExpect(jsonPath("$.anniversaryAutomationEnabled").value(false));

        // 3. Verify client appears in GET /api/admin/anniversary/unverified
        mockMvc.perform(get("/api/admin/anniversary/unverified")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.userId == 'legacy_client_01')]").exists());

        // 4. Update legacy client to COMPANY via PUT /api/admin/users/{userId}
        mockMvc.perform(put("/api/admin/users/legacy_client_01")
                        .header("Authorization", getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "clientType": "COMPANY",
                                    "companyName": "Legacy Modernized Ltd",
                                    "formationDate": "2015-08-20",
                                    "anniversaryAutomationEnabled": "true",
                                    "whatsappConsentGiven": "true"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientType").value("COMPANY"))
                .andExpect(jsonPath("$.companyName").value("Legacy Modernized Ltd"))
                .andExpect(jsonPath("$.formationDate").value("2015-08-20"))
                .andExpect(jsonPath("$.anniversaryAutomationEnabled").value(true));

        // 5. Verify it is no longer unverified
        mockMvc.perform(get("/api/admin/anniversary/unverified")
                        .header("Authorization", getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.userId == 'legacy_client_01')]").doesNotExist());
    }
}
