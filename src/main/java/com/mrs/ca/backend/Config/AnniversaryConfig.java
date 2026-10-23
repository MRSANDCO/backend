package com.mrs.ca.backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Configuration properties dedicated to Company Formation Anniversary Greetings.
 * Kept isolated from existing WhatsApp properties, with safe defaults.
 */
@Configuration
public class AnniversaryConfig {

    public enum LeapDayPolicy {
        FEB_28,
        MARCH_1
    }

    public enum ContactFallbackPolicy {
        USE_FALLBACK_VALUE,
        SKIP
    }

    @Value("${whatsapp.anniversary.template-name:company_anniversary_greeting}")
    private String templateName;

    @Value("${whatsapp.anniversary.template-language:en}")
    private String templateLanguage;

    @Value("${whatsapp.anniversary.contact-fallback-policy:USE_FALLBACK_VALUE}")
    private String contactFallbackPolicy;

    @Value("${whatsapp.anniversary.fallback-contact-name:Valued Client}")
    private String fallbackContactName;

    @Value("${whatsapp.anniversary.fallback-company-name:Valued Enterprise}")
    private String fallbackCompanyName;

    @Value("${whatsapp.anniversary.include-years-completed:false}")
    private boolean includeYearsCompleted;

    @Value("${whatsapp.anniversary.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${whatsapp.anniversary.scheduler.cron:0 0 9 * * *}")
    private String schedulerCron;

    @Value("${whatsapp.anniversary.scheduler.timezone:Asia/Kolkata}")
    private String timezone;

    @Value("${whatsapp.anniversary.scheduler.catch-up-hours:24}")
    private int catchUpHours;

    @Value("${whatsapp.anniversary.leap-day-policy:FEB_28}")
    private String leapDayPolicy;

    @Value("${whatsapp.anniversary.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${whatsapp.anniversary.retry.initial-interval-minutes:5}")
    private int retryInitialIntervalMinutes;

    @Value("${whatsapp.anniversary.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${whatsapp.anniversary.sender-name:MRS & Co.}")
    private String senderName;

    @Value("${whatsapp.webhook.verify-token:mrsco_verify_2026}")
    private String webhookVerifyToken;

    @Value("${whatsapp.webhook.app-secret:}")
    private String webhookAppSecret;

    @Value("${whatsapp.anniversary.require-explicit-consent:false}")
    private boolean requireExplicitConsent;

    public boolean isRequireExplicitConsent() {
        return requireExplicitConsent;
    }

    public String getTemplateName() {
        return (templateName != null && !templateName.isBlank())
                ? templateName.trim()
                : "company_anniversary_greeting";
    }

    public String getTemplateLanguage() {
        return (templateLanguage != null && !templateLanguage.isBlank())
                ? templateLanguage.trim()
                : "en";
    }

    public ContactFallbackPolicy getContactFallbackPolicy() {
        if ("SKIP".equalsIgnoreCase(contactFallbackPolicy)) {
            return ContactFallbackPolicy.SKIP;
        }
        return ContactFallbackPolicy.USE_FALLBACK_VALUE;
    }

    public String getFallbackContactName() {
        return (fallbackContactName != null && !fallbackContactName.isBlank())
                ? fallbackContactName.trim()
                : "Valued Client";
    }

    public String getFallbackCompanyName() {
        return (fallbackCompanyName != null && !fallbackCompanyName.isBlank())
                ? fallbackCompanyName.trim()
                : "Valued Enterprise";
    }

    public boolean isIncludeYearsCompleted() {
        return includeYearsCompleted;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public String getSchedulerCron() {
        return schedulerCron;
    }

    public String getTimezone() {
        return timezone;
    }

    public ZoneId getZoneId() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    public int getCatchUpHours() {
        return catchUpHours;
    }

    public LeapDayPolicy getLeapDayPolicy() {
        if ("MARCH_1".equalsIgnoreCase(leapDayPolicy)) {
            return LeapDayPolicy.MARCH_1;
        }
        return LeapDayPolicy.FEB_28;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public int getRetryInitialIntervalMinutes() {
        return retryInitialIntervalMinutes;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public String getWebhookAppSecret() {
        return webhookAppSecret;
    }
}
