package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import com.mrs.ca.backend.Repositories.FounderRepository;
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

/**
 * Unit tests for {@link BirthdayGreetingService}.
 * WhatsApp API calls are fully mocked. No real messages are sent.
 */
@ExtendWith(MockitoExtension.class)
class BirthdayGreetingServiceTest {

    @Mock private BirthdayConfig birthdayConfig;
    @Mock private WhatsAppService whatsAppService;
    @Mock private AnniversaryNotificationLogRepository logRepository;
    @Mock private FounderRepository founderRepository;
    @Mock private AnniversaryEligibilityService phoneValidator;
    @Mock private MongoTemplate mongoTemplate;

    private BirthdayGreetingService greetingService;

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        lenient().when(birthdayConfig.getTemplateName()).thenReturn("founder_birthday_greeting");
        lenient().when(birthdayConfig.getTemplateLanguage()).thenReturn("en");
        lenient().when(birthdayConfig.getMaxRetryAttempts()).thenReturn(3);
        lenient().when(birthdayConfig.getRetryInitialIntervalMinutes()).thenReturn(5);
        lenient().when(birthdayConfig.getRetryMultiplier()).thenReturn(2.0);
        lenient().when(birthdayConfig.isRequireExplicitConsent()).thenReturn(true);

        greetingService = new BirthdayGreetingService(
                birthdayConfig, whatsAppService, logRepository, founderRepository, phoneValidator, mongoTemplate);
    }

    // ─── Helper factories ────────────────────────────────────────────────────────

    private Founder activeFounder() {
        Founder f = new Founder();
        f.setId("founder_001");
        f.setCompanyClientId("MRS001");
        f.setFullName("Ravi Shankar");
        f.setDateOfBirth(LocalDate.of(1980, 6, 15));
        f.setWhatsappNumber("919876543210");
        f.setActive(true);
        f.setBirthdayGreetingEnabled(true);
        f.setWhatsappConsentGiven(true);
        return f;
    }

    private AnniversaryNotificationLog buildLog(String id, AnniversaryNotificationStatus status) {
        AnniversaryNotificationLog l = new AnniversaryNotificationLog(
                "founder_001", "MRS001", "Ravi Shankar", "Ravi Shankar", "919876543210",
                2026, BUSINESS_DATE);
        l.setId(id);
        l.setNotificationType(AnniversaryNotificationType.FOUNDER_BIRTHDAY);
        l.setStatus(status);
        return l;
    }

    // ─── Success path ────────────────────────────────────────────────────────────

    @Test
    void processFounderBirthday_Success_ReturnsAcceptedLog() {
        Founder founder = activeFounder();
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog saved = buildLog("log_001", AnniversaryNotificationStatus.PENDING);
        when(logRepository.save(any())).thenReturn(saved);

        AnniversaryNotificationLog claimed = buildLog("log_001", AnniversaryNotificationStatus.PROCESSING);
        AnniversaryNotificationLog accepted = buildLog("log_001", AnniversaryNotificationStatus.ACCEPTED);
        accepted.setMetaMessageId("wamid.birthday001");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimed)
                .thenReturn(accepted);

        // Template: {{1}} = founder name only (single-variable birthday template)
        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("founder_birthday_greeting"),
                eq("en"),
                eq(java.util.List.of("Ravi Shankar"))))
                .thenReturn(WhatsAppSendResult.success("wamid.birthday001", 200));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.ACCEPTED);
        assertThat(result.get().getMetaMessageId()).isEqualTo("wamid.birthday001");
    }

    // ─── Idempotency / duplicate prevention ─────────────────────────────────────

    @Test
    void processFounderBirthday_AlreadyAccepted_ReturnsEmpty() {
        Founder founder = activeFounder();
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        AnniversaryNotificationLog existing = buildLog("log_001", AnniversaryNotificationStatus.ACCEPTED);
        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.of(existing));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    @Test
    void processFounderBirthday_AlreadyDelivered_ReturnsEmpty() {
        Founder founder = activeFounder();
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        AnniversaryNotificationLog existing = buildLog("log_001", AnniversaryNotificationStatus.DELIVERED);
        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.of(existing));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    // ─── Eligibility guards ─────────────────────────────────────────────────────

    @Test
    void processFounderBirthday_InactiveFounder_ReturnsEmpty() {
        Founder founder = activeFounder();
        founder.setActive(false);

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    @Test
    void processFounderBirthday_GreetingDisabled_ReturnsEmpty() {
        Founder founder = activeFounder();
        founder.setBirthdayGreetingEnabled(false);

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    @Test
    void processFounderBirthday_NoConsent_ReturnsEmpty() {
        Founder founder = activeFounder();
        founder.setWhatsappConsentGiven(false);

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    @Test
    void processFounderBirthday_InvalidPhone_ReturnsEmpty() {
        Founder founder = activeFounder();
        founder.setWhatsappNumber("invalid");
        when(phoneValidator.normalizeAndValidatePhone("invalid")).thenReturn(null);

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }

    // ─── Notification type isolation ─────────────────────────────────────────────

    @Test
    void processFounderBirthday_UsesFounderBirthdayType_NotAnniversaryType() {
        Founder founder = activeFounder();
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog saved = buildLog("log_001", AnniversaryNotificationStatus.PENDING);
        when(logRepository.save(any())).thenReturn(saved);

        AnniversaryNotificationLog claimed = buildLog("log_001", AnniversaryNotificationStatus.PROCESSING);
        AnniversaryNotificationLog accepted = buildLog("log_001", AnniversaryNotificationStatus.ACCEPTED);
        accepted.setMetaMessageId("wamid.x");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimed).thenReturn(accepted);

        when(whatsAppService.sendTemplateMessage(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.success("wamid.x", 200));

        greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        // Verify FOUNDER_BIRTHDAY type was queried — not COMPANY_FORMATION_ANNIVERSARY
        verify(logRepository).findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY);
        verify(logRepository, never()).findByClientIdAndAnniversaryYearAndNotificationType(
                any(), anyInt(), eq(AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY));
    }

    // ─── Ambiguous / failure outcome ─────────────────────────────────────────────

    @Test
    void processFounderBirthday_NetworkTimeout_RecordedAsAmbiguous() {
        Founder founder = activeFounder();
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "founder_001", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog saved = buildLog("log_001", AnniversaryNotificationStatus.PENDING);
        when(logRepository.save(any())).thenReturn(saved);

        AnniversaryNotificationLog claimed = buildLog("log_001", AnniversaryNotificationStatus.PROCESSING);
        AnniversaryNotificationLog ambiguous = buildLog("log_001", AnniversaryNotificationStatus.AMBIGUOUS);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimed).thenReturn(ambiguous);

        when(whatsAppService.sendTemplateMessage(any(), any(), any(), any()))
                .thenReturn(WhatsAppSendResult.ambiguous(0, "NETWORK_TIMEOUT", "Socket timeout"));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.AMBIGUOUS);
    }

    // ─── Template name / language verification ───────────────────────────────────

    @Test
    void processFounderBirthday_UsesCorrectTemplateNameAndLanguage() {
        Founder founder = activeFounder();
        when(birthdayConfig.getTemplateName()).thenReturn("founder_birthday_greeting");
        when(birthdayConfig.getTemplateLanguage()).thenReturn("en");
        when(phoneValidator.normalizeAndValidatePhone("919876543210")).thenReturn("919876543210");

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog saved = buildLog("log_001", AnniversaryNotificationStatus.PENDING);
        when(logRepository.save(any())).thenReturn(saved);

        AnniversaryNotificationLog claimed = buildLog("log_001", AnniversaryNotificationStatus.PROCESSING);
        AnniversaryNotificationLog accepted = buildLog("log_001", AnniversaryNotificationStatus.ACCEPTED);
        accepted.setMetaMessageId("wamid.tmpl");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimed).thenReturn(accepted);

        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("founder_birthday_greeting"),   // must be exact template name
                eq("en"),                          // must be exact language code
                eq(java.util.List.of("Ravi Shankar"))))
                .thenReturn(WhatsAppSendResult.success("wamid.tmpl", 200));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founder, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isPresent();
        // Verify sendTemplateMessage was called with exactly the right template/language
        verify(whatsAppService).sendTemplateMessage(
                eq("919876543210"),
                eq("founder_birthday_greeting"),
                eq("en"),
                eq(java.util.List.of("Ravi Shankar")));
    }

    @Test
    void processIndividualBirthday_Success_ReturnsAcceptedLog() {
        com.mrs.ca.backend.Models.User user = new com.mrs.ca.backend.Models.User();
        user.setUserId("user_ind_1");
        user.setFullName("Priya Sharma");
        user.setClientType(com.mrs.ca.backend.Models.ClientType.INDIVIDUAL);
        user.setDateOfBirth(LocalDate.of(1992, 6, 15));
        user.setPhone("9876543210");
        user.setActive(true);
        user.setBirthdayGreetingEnabled(true);
        user.setWhatsappConsentGiven(true);

        when(phoneValidator.normalizeAndValidatePhone("9876543210")).thenReturn("919876543210");
        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "user_ind_1", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.empty());

        AnniversaryNotificationLog saved = new AnniversaryNotificationLog(
                "user_ind_1", "INDIVIDUAL", "Priya Sharma", "Priya Sharma", "919876543210", 2026, BUSINESS_DATE);
        saved.setId("log_ind_001");
        saved.setStatus(AnniversaryNotificationStatus.PENDING);
        when(logRepository.save(any())).thenReturn(saved);

        AnniversaryNotificationLog claimed = new AnniversaryNotificationLog(
                "user_ind_1", "INDIVIDUAL", "Priya Sharma", "Priya Sharma", "919876543210", 2026, BUSINESS_DATE);
        claimed.setId("log_ind_001");
        claimed.setStatus(AnniversaryNotificationStatus.PROCESSING);

        AnniversaryNotificationLog accepted = new AnniversaryNotificationLog(
                "user_ind_1", "INDIVIDUAL", "Priya Sharma", "Priya Sharma", "919876543210", 2026, BUSINESS_DATE);
        accepted.setId("log_ind_001");
        accepted.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        accepted.setMetaMessageId("wamid.ind001");

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class)))
                .thenReturn(claimed)
                .thenReturn(accepted);

        when(whatsAppService.sendTemplateMessage(
                eq("919876543210"),
                eq("founder_birthday_greeting"),
                eq("en"),
                eq(java.util.List.of("Priya Sharma"))))
                .thenReturn(WhatsAppSendResult.success("wamid.ind001", 200));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processIndividualBirthday(user, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnniversaryNotificationStatus.ACCEPTED);
        assertThat(result.get().getMetaMessageId()).isEqualTo("wamid.ind001");
    }

    @Test
    void processIndividualBirthday_AlreadySent_ReturnsEmpty() {
        com.mrs.ca.backend.Models.User user = new com.mrs.ca.backend.Models.User();
        user.setUserId("user_ind_1");
        user.setFullName("Priya Sharma");
        user.setClientType(com.mrs.ca.backend.Models.ClientType.INDIVIDUAL);
        user.setDateOfBirth(LocalDate.of(1992, 6, 15));
        user.setPhone("9876543210");
        user.setActive(true);
        user.setBirthdayGreetingEnabled(true);
        user.setWhatsappConsentGiven(true);

        when(phoneValidator.normalizeAndValidatePhone("9876543210")).thenReturn("919876543210");

        AnniversaryNotificationLog existing = new AnniversaryNotificationLog(
                "user_ind_1", "INDIVIDUAL", "Priya Sharma", "Priya Sharma", "919876543210", 2026, BUSINESS_DATE);
        existing.setId("log_ind_001");
        existing.setStatus(AnniversaryNotificationStatus.ACCEPTED);

        when(logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                "user_ind_1", 2026, AnniversaryNotificationType.FOUNDER_BIRTHDAY))
                .thenReturn(Optional.of(existing));

        Optional<AnniversaryNotificationLog> result =
                greetingService.processIndividualBirthday(user, BUSINESS_DATE, false, "SCHEDULER");

        assertThat(result).isEmpty();
        verify(whatsAppService, never()).sendTemplateMessage(any(), any(), any(), any());
    }
}
