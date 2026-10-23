package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Config.WhatsAppConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnniversaryEligibilityServiceTest {

    @Mock
    private AnniversaryMatchingService matchingService;

    @Mock
    private AnniversaryConfig anniversaryConfig;

    @Mock
    private WhatsAppConfig whatsAppConfig;

    @Mock
    private AnniversaryNotificationLogRepository logRepository;

    private AnniversaryEligibilityService eligibilityService;

    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        eligibilityService = new AnniversaryEligibilityService(
                matchingService, anniversaryConfig, whatsAppConfig, logRepository);

        lenient().when(anniversaryConfig.getTemplateName()).thenReturn("company_anniversary_greeting");
        lenient().when(anniversaryConfig.getContactFallbackPolicy()).thenReturn(AnniversaryConfig.ContactFallbackPolicy.USE_FALLBACK_VALUE);
        lenient().when(whatsAppConfig.getAccessToken()).thenReturn("valid_token");
        lenient().when(whatsAppConfig.getPhoneNumberId()).thenReturn("valid_phone_id");
    }

    private User createValidClient() {
        User user = new User();
        user.setUserId("client_101");
        user.setFullName("Ramesh Kumar");
        user.setClientType(ClientType.COMPANY);
        user.setCompanyName("Alpha Technologies Pvt. Ltd.");
        user.setPhone("9876543210");
        user.setFormationDate(LocalDate.of(2018, 10, 10));
        user.setActive(true);
        user.setAnniversaryAutomationEnabled(true);
        user.setWhatsappConsentGiven(true);
        return user;
    }

    @Test
    void testEligibleClient() {
        User client = createValidClient();
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);
        when(logRepository.existsByClientIdAndAnniversaryYearAndNotificationType(
                eq("client_101"), eq(2026), eq(AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY)))
                .thenReturn(false);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getNormalizedPhone()).isEqualTo("919876543210");
    }

    @Test
    void testIneligible_AutomationDisabled() {
        User client = createValidClient();
        client.setAnniversaryAutomationEnabled(false);
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("AUTOMATION_DISABLED");
    }

    @Test
    void testIneligible_ConsentMissing() {
        User client = createValidClient();
        client.setWhatsappConsentGiven(false);
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("CONSENT_MISSING_OR_WITHDRAWN");
    }

    @Test
    void testIneligible_FormationDateMissing() {
        User client = createValidClient();
        client.setFormationDate(null);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("FORMATION_DATE_MISSING");
    }

    @Test
    void testIneligible_ClientInactive() {
        User client = createValidClient();
        client.setActive(false);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("CLIENT_INACTIVE");
    }

    @Test
    void testIneligible_NotAnniversaryToday() {
        User client = createValidClient();
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(false);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("NOT_ANNIVERSARY_TODAY");
    }

    @Test
    void testIneligible_AlreadyProcessedThisYear() {
        User client = createValidClient();
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);
        when(logRepository.existsByClientIdAndAnniversaryYearAndNotificationType(
                eq("client_101"), eq(2026), eq(AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY)))
                .thenReturn(true);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("ALREADY_PROCESSED_THIS_YEAR");
    }

    @Test
    void testIneligible_IndividualClient_Excluded() {
        User client = createValidClient();
        client.setClientType(ClientType.INDIVIDUAL);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("INDIVIDUAL_CLIENT_EXCLUDED");
    }

    @Test
    void testIneligible_ClientTypeUnresolved() {
        User client = createValidClient();
        client.setClientType(null);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("CLIENT_TYPE_UNRESOLVED");
    }

    @Test
    void testIneligible_MissingPhoneNumber() {
        User client = createValidClient();
        client.setPhone(null);
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("PHONE_NUMBER_MISSING");
    }

    @Test
    void testIneligible_InvalidPhoneNumber() {
        User client = createValidClient();
        client.setPhone("123"); // Too short to be valid
        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("INVALID_PHONE_NUMBER");
    }

    @Test
    void testPhoneNormalization() {
        assertThat(eligibilityService.normalizeAndValidatePhone("9876543210")).isEqualTo("919876543210");
        assertThat(eligibilityService.normalizeAndValidatePhone("09876543210")).isEqualTo("919876543210");
        assertThat(eligibilityService.normalizeAndValidatePhone("+91 98765 43210")).isEqualTo("919876543210");
        assertThat(eligibilityService.normalizeAndValidatePhone("123")).isNull(); // Too short
        assertThat(eligibilityService.normalizeAndValidatePhone("123456789012345678")).isNull(); // Too long
    }

    @Test
    void testIneligible_ContactNameMissing_WhenFallbackPolicySkip() {
        User client = createValidClient();
        client.setContactPersonName("");
        client.setFullName("");

        when(matchingService.isAnniversaryToday(client.getFormationDate(), today)).thenReturn(true);
        when(anniversaryConfig.getContactFallbackPolicy()).thenReturn(AnniversaryConfig.ContactFallbackPolicy.SKIP);

        var result = eligibilityService.evaluateEligibility(client, today);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).isEqualTo("CONTACT_NAME_MISSING");
    }
}
