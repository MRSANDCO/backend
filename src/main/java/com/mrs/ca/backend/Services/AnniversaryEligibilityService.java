package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Config.WhatsAppConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Service that verifies eligibility rules and phone number validity before processing anniversary greetings.
 */
@Service
public class AnniversaryEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(AnniversaryEligibilityService.class);

    private final AnniversaryMatchingService matchingService;
    private final AnniversaryConfig anniversaryConfig;
    private final WhatsAppConfig whatsAppConfig;
    private final AnniversaryNotificationLogRepository logRepository;

    public AnniversaryEligibilityService(AnniversaryMatchingService matchingService,
                                       AnniversaryConfig anniversaryConfig,
                                       WhatsAppConfig whatsAppConfig,
                                       AnniversaryNotificationLogRepository logRepository) {
        this.matchingService = matchingService;
        this.anniversaryConfig = anniversaryConfig;
        this.whatsAppConfig = whatsAppConfig;
        this.logRepository = logRepository;
    }

    public static class EligibilityResult {
        private final boolean eligible;
        private final String reason;
        private final String normalizedPhone;

        private EligibilityResult(boolean eligible, String reason, String normalizedPhone) {
            this.eligible = eligible;
            this.reason = reason;
            this.normalizedPhone = normalizedPhone;
        }

        public static EligibilityResult eligible(String normalizedPhone) {
            return new EligibilityResult(true, "ELIGIBLE", normalizedPhone);
        }

        public static EligibilityResult ineligible(String reason) {
            return new EligibilityResult(false, reason, null);
        }

        public boolean isEligible() {
            return eligible;
        }

        public String getReason() {
            return reason;
        }

        public String getNormalizedPhone() {
            return normalizedPhone;
        }
    }

    /**
     * Evaluates whether a client is eligible to receive an anniversary greeting on the specified business date.
     */
    public EligibilityResult evaluateEligibility(User client, LocalDate businessDate) {
        if (client == null) {
            return EligibilityResult.ineligible("CLIENT_NOT_FOUND");
        }

        if (!client.isActive()) {
            return EligibilityResult.ineligible("CLIENT_INACTIVE");
        }

        // Only clients explicitly identified as COMPANY are eligible.
        // Individual clients are excluded; unverified records are held until administrator verification.
        if (client.getClientType() == null) {
            return EligibilityResult.ineligible("CLIENT_TYPE_UNRESOLVED");
        }

        if (client.getClientType() == ClientType.INDIVIDUAL) {
            return EligibilityResult.ineligible("INDIVIDUAL_CLIENT_EXCLUDED");
        }

        if (client.getClientType() != ClientType.COMPANY) {
            return EligibilityResult.ineligible("CLIENT_TYPE_NOT_COMPANY");
        }

        if (client.getFormationDate() == null) {
            return EligibilityResult.ineligible("FORMATION_DATE_MISSING");
        }

        if (!matchingService.isAnniversaryToday(client.getFormationDate(), businessDate)) {
            return EligibilityResult.ineligible("NOT_ANNIVERSARY_TODAY");
        }

        if (!client.isAnniversaryAutomationEnabled()) {
            return EligibilityResult.ineligible("AUTOMATION_DISABLED");
        }

        if (!client.isWhatsappConsentGiven()) {
            return EligibilityResult.ineligible("CONSENT_MISSING_OR_WITHDRAWN");
        }

        String phone = client.getPhone();
        if (phone == null || phone.isBlank()) {
            return EligibilityResult.ineligible("PHONE_NUMBER_MISSING");
        }

        String normalizedPhone = normalizeAndValidatePhone(phone);
        if (normalizedPhone == null) {
            return EligibilityResult.ineligible("INVALID_PHONE_NUMBER");
        }

        // Verify contact name availability if fallback policy is SKIP
        if (anniversaryConfig.getContactFallbackPolicy() == AnniversaryConfig.ContactFallbackPolicy.SKIP) {
            String contact = client.getContactPersonName();
            String full = client.getFullName();
            if ((contact == null || contact.isBlank()) && (full == null || full.isBlank())) {
                return EligibilityResult.ineligible("CONTACT_NAME_MISSING");
            }
        }

        // Verify template configuration
        String templateName = anniversaryConfig.getTemplateName();
        if (templateName == null || templateName.isBlank()) {
            return EligibilityResult.ineligible("TEMPLATE_CONFIG_MISSING");
        }

        // Check if Meta access token and phone number ID are configured
        if (whatsAppConfig.getAccessToken() == null || whatsAppConfig.getAccessToken().isBlank() ||
                whatsAppConfig.getPhoneNumberId() == null || whatsAppConfig.getPhoneNumberId().isBlank()) {
            return EligibilityResult.ineligible("WHATSAPP_CONFIG_MISSING");
        }

        // Duplicate check for this anniversary year and notification type
        int anniversaryYear = businessDate.getYear();
        boolean alreadyProcessed = logRepository.existsByClientIdAndAnniversaryYearAndNotificationType(
                client.getUserId(), anniversaryYear, AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY);

        if (alreadyProcessed) {
            return EligibilityResult.ineligible("ALREADY_PROCESSED_THIS_YEAR");
        }

        return EligibilityResult.eligible(normalizedPhone);
    }

    /**
     * Normalizes and validates recipient phone number.
     * Meta WhatsApp requires digits with country code, typically 10 to 15 digits.
     * Default assumption for 10-digit Indian numbers without country code is prefixing with '91'.
     */
    public String normalizeAndValidatePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");

        if (digits.length() == 10) {
            // Standard 10-digit Indian mobile number
            digits = "91" + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            // 0-prefixed 10-digit number
            digits = "91" + digits.substring(1);
        }

        // Valid international WhatsApp numbers range from 10 to 15 digits with country code
        if (digits.length() < 10 || digits.length() > 15) {
            log.warn("[PHONE-VALIDATION] Rejecting phone number with invalid digit count: length={}", digits.length());
            return null;
        }

        return digits;
    }
}
