package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.FounderRepository;
import com.mrs.ca.backend.Services.BirthdayGreetingService;
import com.mrs.ca.backend.Services.BirthdaySchedulerService;
import com.mrs.ca.backend.Services.FounderService;
import com.mrs.ca.backend.Services.WhatsAppService;
import com.mrs.ca.backend.dto.FounderRequest;
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
class AdminFounderControllerTest {

    @Mock
    private FounderService founderService;

    @Mock
    private FounderRepository founderRepository;

    @Mock
    private BirthdayGreetingService greetingService;

    @Mock
    private BirthdaySchedulerService schedulerService;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private BirthdayConfig birthdayConfig;

    @Mock
    private AnniversaryNotificationLogRepository logRepository;

    @Mock
    private Authentication authentication;

    private AdminFounderController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminFounderController(
                founderService,
                founderRepository,
                greetingService,
                schedulerService,
                whatsAppService,
                birthdayConfig,
                logRepository
        );
    }

    @Test
    void createFounder_success_returns201() {
        FounderRequest req = new FounderRequest();
        req.setCompanyClientId("MRS001");
        req.setFullName("Ravi Shankar");
        req.setDateOfBirth(LocalDate.of(1985, 6, 15));
        req.setWhatsappNumber("919876543210");

        Founder f = new Founder();
        f.setId("f-1");
        f.setFullName("Ravi Shankar");
        f.setCompanyClientId("MRS001");

        when(founderService.createFounder(any(FounderRequest.class))).thenReturn(f);

        ResponseEntity<?> response = controller.createFounder(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Founder created successfully");
        assertThat(body.get("founder")).isEqualTo(f);
    }

    @Test
    void createFounder_badRequest_returns400() {
        FounderRequest req = new FounderRequest();
        when(founderService.createFounder(any(FounderRequest.class)))
                .thenThrow(new IllegalArgumentException("Client not found"));

        ResponseEntity<?> response = controller.createFounder(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error")).isEqualTo("Client not found");
    }

    @Test
    void getFoundersByCompany_returnsList() {
        Founder f1 = new Founder();
        f1.setId("f-1");
        when(founderService.getFoundersByCompany("MRS001")).thenReturn(List.of(f1));

        ResponseEntity<List<Founder>> response = controller.getFoundersByCompany("MRS001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(f1);
    }

    @Test
    void getFounder_found_returns200() {
        Founder f = new Founder();
        f.setId("f-1");
        when(founderService.findById("f-1")).thenReturn(Optional.of(f));

        ResponseEntity<?> response = controller.getFounder("f-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(f);
    }

    @Test
    void getFounder_notFound_returns404() {
        when(founderService.findById("f-none")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getFounder("f-none");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateFounder_success_returns200() {
        FounderRequest req = new FounderRequest();
        req.setFullName("Ravi S");
        Founder updated = new Founder();
        updated.setId("f-1");
        updated.setFullName("Ravi S");

        when(founderService.updateFounder(eq("f-1"), any(FounderRequest.class))).thenReturn(updated);

        ResponseEntity<?> response = controller.updateFounder("f-1", req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void setBirthdayGreetingEnabled_valid_returns200() {
        Founder updated = new Founder();
        updated.setId("f-1");
        updated.setBirthdayGreetingEnabled(true);

        when(founderService.setBirthdayGreetingEnabled("f-1", true)).thenReturn(updated);

        ResponseEntity<?> response = controller.setBirthdayGreetingEnabled("f-1", Map.of("enabled", true));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void setFounderActive_valid_returns200() {
        Founder updated = new Founder();
        updated.setId("f-1");
        updated.setActive(false);

        when(founderService.setActive("f-1", false)).thenReturn(updated);

        ResponseEntity<?> response = controller.setFounderActive("f-1", Map.of("active", false));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteFounder_success_returns200() {
        doNothing().when(founderService).deleteFounder("f-1");

        ResponseEntity<?> response = controller.deleteFounder("f-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getTodayBirthdayFounders_returnsFounders() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        when(schedulerService.getCurrentBusinessDate()).thenReturn(today);

        Founder f = new Founder();
        f.setId("f-1");
        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(6, 15))
                .thenReturn(List.of(f));

        ResponseEntity<List<Founder>> response = controller.getTodayBirthdayFounders();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(f);
    }

    @Test
    void triggerBirthdayJobNow_returnsSummary() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        when(schedulerService.getCurrentBusinessDate()).thenReturn(today);
        when(authentication.getName()).thenReturn("adminUser");

        BirthdaySchedulerService.ExecutionSummary summary =
                new BirthdaySchedulerService.ExecutionSummary(today);
        when(schedulerService.executeBirthdayJobForDate(today, "adminUser")).thenReturn(summary);

        ResponseEntity<?> response = controller.triggerBirthdayJobNow(authentication);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Birthday scheduler job completed");
    }

    @Test
    void triggerBirthdayForFounder_founderFound_processed_returns200() {
        Founder f = new Founder();
        f.setId("f-1");
        when(founderService.findById("f-1")).thenReturn(Optional.of(f));
        when(schedulerService.getCurrentBusinessDate()).thenReturn(LocalDate.of(2026, 6, 15));
        when(authentication.getName()).thenReturn("adminUser");

        AnniversaryNotificationLog log = new AnniversaryNotificationLog();
        log.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        when(greetingService.processFounderBirthday(eq(f), eq(LocalDate.of(2026, 6, 15)), eq(true), eq("adminUser")))
                .thenReturn(Optional.of(log));

        ResponseEntity<?> response = controller.triggerBirthdayForFounder("f-1", null, authentication);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sendTestBirthdayGreeting_accepted_returns200() {
        when(birthdayConfig.getTemplateName()).thenReturn("founder_birthday_greeting");
        when(birthdayConfig.getTemplateLanguage()).thenReturn("en_US");
        when(whatsAppService.sendTemplateMessage(eq("919876543210"), eq("founder_birthday_greeting"), eq("en_US"), anyList()))
                .thenReturn(WhatsAppSendResult.success("wamid.test", 200));

        ResponseEntity<?> response = controller.sendTestBirthdayGreeting(
                Map.of("recipientPhone", "919876543210", "founderName", "Ravi Shankar")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("metaMessageId")).isEqualTo("wamid.test");
    }

    @Test
    void sendTestBirthdayGreeting_rejected_returns502() {
        when(birthdayConfig.getTemplateName()).thenReturn("founder_birthday_greeting");
        when(birthdayConfig.getTemplateLanguage()).thenReturn("en_US");
        when(whatsAppService.sendTemplateMessage(eq("919876543210"), eq("founder_birthday_greeting"), eq("en_US"), anyList()))
                .thenReturn(WhatsAppSendResult.failure(400, "100", "Template does not exist", false));

        ResponseEntity<?> response = controller.sendTestBirthdayGreeting(
                Map.of("recipientPhone", "919876543210", "founderName", "Ravi Shankar")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("FAILED");
        assertThat(body.get("errorCode")).isEqualTo("100");
    }
}
