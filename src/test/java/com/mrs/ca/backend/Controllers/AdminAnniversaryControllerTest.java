package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.Services.AnniversaryGreetingService;
import com.mrs.ca.backend.Services.AnniversaryMatchingService;
import com.mrs.ca.backend.Services.AnniversarySchedulerService;
import com.mrs.ca.backend.Services.WhatsAppService;
import com.mrs.ca.backend.dto.AnniversaryClientDto;
import com.mrs.ca.backend.dto.AnniversarySettingsRequest;
import com.mrs.ca.backend.dto.AnniversaryTestSendRequest;
import com.mrs.ca.backend.dto.WhatsAppSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAnniversaryControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnniversaryNotificationLogRepository logRepository;

    @Mock
    private AnniversaryMatchingService matchingService;

    @Mock
    private AnniversaryGreetingService greetingService;

    @Mock
    private AnniversarySchedulerService schedulerService;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private AnniversaryConfig anniversaryConfig;

    @Mock
    private Authentication auth;

    private AdminAnniversaryController controller;

    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        controller = new AdminAnniversaryController(
                userRepository, logRepository, matchingService,
                greetingService, schedulerService, whatsAppService, anniversaryConfig);

        lenient().when(matchingService.getCurrentBusinessDate()).thenReturn(today);
        lenient().when(anniversaryConfig.getTemplateName()).thenReturn("company_anniversary_greeting");
        lenient().when(anniversaryConfig.getTemplateLanguage()).thenReturn("en");
        lenient().when(anniversaryConfig.getFallbackContactName()).thenReturn("Valued Client");
        lenient().when(anniversaryConfig.getFallbackCompanyName()).thenReturn("Valued Enterprise");
    }

    private User createClient(String userId, String companyName, LocalDate formationDate) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName("Contact Person");
        user.setClientType(ClientType.COMPANY);
        user.setCompanyName(companyName);
        user.setPhone("9876543210");
        user.setFormationDate(formationDate);
        user.setActive(true);
        user.setAnniversaryAutomationEnabled(true);
        user.setWhatsappConsentGiven(true);
        return user;
    }

    @Test
    void testGetTodayAnniversaryClients() {
        User client1 = createClient("c1", "Company One", LocalDate.of(2018, 10, 10));
        User client2 = createClient("c2", "Company Two", LocalDate.of(2019, 5, 5));

        when(userRepository.findByActiveTrueAndFormationDateIsNotNull()).thenReturn(List.of(client1, client2));
        when(matchingService.isAnniversaryToday(LocalDate.of(2018, 10, 10), today)).thenReturn(true);
        when(matchingService.isAnniversaryToday(LocalDate.of(2019, 5, 5), today)).thenReturn(false);

        ResponseEntity<List<AnniversaryClientDto>> response = controller.getTodayAnniversaryClients();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getUserId()).isEqualTo("c1");
    }

    @Test
    void testIndividualClientExcludedFromTodayAnniversaries() {
        User companyClient = createClient("comp1", "Company One", LocalDate.of(2018, 10, 10));
        User individualClient = createClient("ind1", null, LocalDate.of(2018, 10, 10));
        individualClient.setClientType(ClientType.INDIVIDUAL);

        when(userRepository.findByActiveTrueAndFormationDateIsNotNull()).thenReturn(List.of(companyClient, individualClient));
        when(matchingService.isAnniversaryToday(LocalDate.of(2018, 10, 10), today)).thenReturn(true);

        ResponseEntity<List<AnniversaryClientDto>> response = controller.getTodayAnniversaryClients();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getUserId()).isEqualTo("comp1");
    }

    @Test
    void testGetUnverifiedClients() {
        User verified = createClient("v1", "Company", LocalDate.of(2018, 10, 10));
        User unverified = createClient("uv1", "Unknown", LocalDate.of(2018, 10, 10));
        unverified.setClientType(null);

        when(userRepository.findAll()).thenReturn(List.of(verified, unverified));

        ResponseEntity<List<AnniversaryClientDto>> response = controller.getUnverifiedClients();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getUserId()).isEqualTo("uv1");
    }

    @Test
    void testUpdateClientAnniversarySettings() {
        User client = createClient("c1", "Old Firm", LocalDate.of(2018, 10, 10));
        when(userRepository.findByUserId("c1")).thenReturn(Optional.of(client));

        AnniversarySettingsRequest request = new AnniversarySettingsRequest();
        request.setClientType(ClientType.COMPANY);
        request.setCompanyName("New Firm Pvt. Ltd.");
        request.setAnniversaryAutomationEnabled(true);
        request.setWhatsappConsentGiven(true);
        request.setFormationDate(LocalDate.of(2017, 4, 15));

        ResponseEntity<?> response = controller.updateClientAnniversarySettings("c1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(client);
        assertThat(client.getClientType()).isEqualTo(ClientType.COMPANY);
        assertThat(client.getCompanyName()).isEqualTo("New Firm Pvt. Ltd.");
        assertThat(client.getFormationDate()).isEqualTo(LocalDate.of(2017, 4, 15));
        assertThat(client.isAnniversaryAutomationEnabled()).isTrue();
        assertThat(client.isWhatsappConsentGiven()).isTrue();
    }

    @Test
    void testUpdateSettings_RejectAutomationForIndividual() {
        User client = createClient("c_ind", null, null);
        client.setClientType(ClientType.INDIVIDUAL);
        when(userRepository.findByUserId("c_ind")).thenReturn(Optional.of(client));

        AnniversarySettingsRequest request = new AnniversarySettingsRequest();
        request.setAnniversaryAutomationEnabled(true);

        ResponseEntity<?> response = controller.updateClientAnniversarySettings("c_ind", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error")).isEqualTo("Anniversary automation cannot be enabled for INDIVIDUAL clients");
    }

    @Test
    void testSendTestGreeting_Success() {
        AnniversaryTestSendRequest request = new AnniversaryTestSendRequest();
        request.setRecipientPhone("919876543210");
        request.setCompanyName("Test Corp");
        request.setContactPersonName("Test Person");

        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("company_anniversary_greeting"),
                eq("en"),
                eq(List.of("Test Person", "Test Corp"))))
                .thenReturn(WhatsAppSendResult.success("wamid.test999", 200));

        ResponseEntity<?> response = controller.sendTestGreeting(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("metaMessageId")).isEqualTo("wamid.test999");
    }

    @Test
    void testSendTestGreeting_FallbackNames_WhenNotProvidedInRequest() {
        AnniversaryTestSendRequest request = new AnniversaryTestSendRequest();
        request.setRecipientPhone("919876543210");
        request.setContactPersonName(null);
        request.setCompanyName(null);

        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("company_anniversary_greeting"),
                eq("en"),
                eq(List.of("Valued Client", "Valued Enterprise"))))
                .thenReturn(WhatsAppSendResult.success("wamid.test999", 200));

        ResponseEntity<?> response = controller.sendTestGreeting(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    void testManuallyTriggerGreeting() {
        User client = createClient("c1", "Company One", LocalDate.of(2018, 10, 10));
        when(userRepository.findByUserId("c1")).thenReturn(Optional.of(client));
        when(auth.getName()).thenReturn("admin_user");

        AnniversaryNotificationLog log = new AnniversaryNotificationLog(
                "c1", "u1", "Company One", "Contact Person", "919876543210", 2026, today);
        log.setStatus(AnniversaryNotificationStatus.ACCEPTED);

        when(greetingService.processClientGreeting(eq(client), eq(today), eq(true), eq("admin_user")))
                .thenReturn(Optional.of(log));

        ResponseEntity<?> response = controller.manuallyTriggerGreeting("c1", auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Greeting processed successfully");
    }

    @Test
    void testTriggerSchedulerNow() {
        when(auth.getName()).thenReturn("admin_user");
        AnniversarySchedulerService.ExecutionSummary summary = new AnniversarySchedulerService.ExecutionSummary(today);
        when(schedulerService.executeAnniversaryJobForDate(eq(today), eq("admin_user"))).thenReturn(summary);

        ResponseEntity<?> response = controller.triggerSchedulerNow(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(schedulerService).executeAnniversaryJobForDate(today, "admin_user");
    }
}
