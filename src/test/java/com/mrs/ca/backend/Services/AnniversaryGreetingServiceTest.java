package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.WhatsAppSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnniversaryGreetingServiceTest {

    @Mock
    private AnniversaryConfig anniversaryConfig;

    @Mock
    private AnniversaryEligibilityService eligibilityService;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private AnniversaryNotificationLogRepository logRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private AnniversaryGreetingService greetingService;

    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        lenient().when(anniversaryConfig.getTemplateName()).thenReturn("company_anniversary_greeting");
        lenient().when(anniversaryConfig.getTemplateLanguage()).thenReturn("en");
        lenient().when(anniversaryConfig.getFallbackContactName()).thenReturn("Valued Client");
        lenient().when(anniversaryConfig.getFallbackCompanyName()).thenReturn("Valued Enterprise");
        lenient().when(anniversaryConfig.getContactFallbackPolicy()).thenReturn(AnniversaryConfig.ContactFallbackPolicy.USE_FALLBACK_VALUE);
        lenient().when(anniversaryConfig.getMaxRetryAttempts()).thenReturn(3);
        lenient().when(anniversaryConfig.getRetryInitialIntervalMinutes()).thenReturn(5);
        lenient().when(anniversaryConfig.getRetryMultiplier()).thenReturn(2.0);

        greetingService = new AnniversaryGreetingService(
                anniversaryConfig, eligibilityService, whatsAppService, logRepository, userRepository, mongoTemplate);
    }

    private User createClient() {
        User user = new User();
        user.setUserId("client_202");
        user.setFullName("Sita Raman");
        user.setCompanyName("Zenith Solutions");
        user.setPhone("9876543210");
        user.setFormationDate(LocalDate.of(2019, 10, 10));
        user.setActive(true);
        user.setAnniversaryAutomationEnabled(true);
        user.setWhatsappConsentGiven(true);
        return user;
    }

    @Test
    void testProcessGreeting_Success() {
        User client = createClient();
        when(eligibilityService.evaluateEligibility(client, today))
                .thenReturn(AnniversaryEligibilityService.EligibilityResult.eligible("919876543210"));

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "client_202", 2026, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog savedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        savedLog.setId("log_202");
        when(logRepository.save(any(AnniversaryNotificationLog.class))).thenReturn(savedLog);

        AnniversaryNotificationLog claimedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        claimedLog.setId("log_202");
        claimedLog.setStatus(AnniversaryNotificationStatus.PROCESSING);

        AnniversaryNotificationLog finalLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        finalLog.setId("log_202");
        finalLog.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        finalLog.setMetaMessageId("wamid.success123");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimedLog)
                .thenReturn(finalLog);

        // Verify template parameters in exact order: {{1}}=Contact Person, {{2}}=Company Name
        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("company_anniversary_greeting"),
                eq("en"),
                eq(java.util.List.of("Sita Raman", "Zenith Solutions"))))
                .thenReturn(WhatsAppSendResult.success("wamid.success123", 200));

        Optional<AnniversaryNotificationLog> result = greetingService.processClientGreeting(
                client, today, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.ACCEPTED);
        assertThat(result.get().getMetaMessageId()).isEqualTo("wamid.success123");
    }

    @Test
    void testProcessGreeting_AmbiguousOutcome_RecordedAsAmbiguous() {
        User client = createClient();
        when(eligibilityService.evaluateEligibility(client, today))
                .thenReturn(AnniversaryEligibilityService.EligibilityResult.eligible("919876543210"));

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "client_202", 2026, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog savedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        savedLog.setId("log_202");
        when(logRepository.save(any(AnniversaryNotificationLog.class))).thenReturn(savedLog);

        AnniversaryNotificationLog claimedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        claimedLog.setId("log_202");
        claimedLog.setStatus(AnniversaryNotificationStatus.PROCESSING);

        AnniversaryNotificationLog finalLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Sita Raman", "919876543210", 2026, today);
        finalLog.setId("log_202");
        finalLog.setStatus(AnniversaryNotificationStatus.AMBIGUOUS);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimedLog)
                .thenReturn(finalLog);

        when(whatsAppService.sendTemplateMessage(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.ambiguous(0, "NETWORK_TIMEOUT", "Socket timeout after request dispatch"));

        Optional<AnniversaryNotificationLog> result = greetingService.processClientGreeting(
                client, today, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.AMBIGUOUS);
    }

    @Test
    void testProcessGreeting_IneligibleClient_Skipped() {
        User client = createClient();
        when(eligibilityService.evaluateEligibility(client, today))
                .thenReturn(AnniversaryEligibilityService.EligibilityResult.ineligible("AUTOMATION_DISABLED"));

        Optional<AnniversaryNotificationLog> result = greetingService.processClientGreeting(
                client, today, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    @Test
    void testProcessGreeting_FallbackContactName_UsedWhenContactNameMissing() {
        User client = createClient();
        client.setContactPersonName(null);
        client.setFullName(null); // Contact name unavailable

        when(eligibilityService.evaluateEligibility(client, today))
                .thenReturn(AnniversaryEligibilityService.EligibilityResult.eligible("919876543210"));

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "client_202", 2026, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog savedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Valued Client", "919876543210", 2026, today);
        savedLog.setId("log_202");
        when(logRepository.save(any(AnniversaryNotificationLog.class))).thenReturn(savedLog);

        AnniversaryNotificationLog claimedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Valued Client", "919876543210", 2026, today);
        claimedLog.setId("log_202");
        claimedLog.setStatus(AnniversaryNotificationStatus.PROCESSING);

        AnniversaryNotificationLog finalLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "Valued Client", "919876543210", 2026, today);
        finalLog.setId("log_202");
        finalLog.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        finalLog.setMetaMessageId("wamid.fallback123");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimedLog)
                .thenReturn(finalLog);

        // Fallback name "Valued Client" should be used for {{1}}
        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("company_anniversary_greeting"),
                eq("en"),
                eq(java.util.List.of("Valued Client", "Zenith Solutions"))))
                .thenReturn(WhatsAppSendResult.success("wamid.fallback123", 200));

        Optional<AnniversaryNotificationLog> result = greetingService.processClientGreeting(
                client, today, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.ACCEPTED);
    }

    @Test
    void testProcessGreeting_FallbackPolicySkip_SkipsWhenContactNameMissing() {
        User client = createClient();
        client.setContactPersonName("");
        client.setFullName("");

        when(eligibilityService.evaluateEligibility(client, today))
                .thenReturn(AnniversaryEligibilityService.EligibilityResult.eligible("919876543210"));

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "client_202", 2026, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog savedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "", "919876543210", 2026, today);
        savedLog.setId("log_202");
        when(logRepository.save(any(AnniversaryNotificationLog.class))).thenReturn(savedLog);

        AnniversaryNotificationLog claimedLog = new AnniversaryNotificationLog(
                "client_202", "uid_1", "Zenith Solutions", "", "919876543210", 2026, today);
        claimedLog.setId("log_202");
        claimedLog.setStatus(AnniversaryNotificationStatus.PROCESSING);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimedLog);

        when(anniversaryConfig.getContactFallbackPolicy()).thenReturn(AnniversaryConfig.ContactFallbackPolicy.SKIP);

        Optional<AnniversaryNotificationLog> result = greetingService.processClientGreeting(
                client, today, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }
}
