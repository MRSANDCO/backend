package com.mrs.ca.backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Configuration properties for the Founder Birthday Greeting automation.
 * <p>
 * All properties default to safe/disabled values so production activation requires
 * explicit environment variable configuration (see README / deployment guide).
 * </p>
 *
 * <p><strong>Required Meta WhatsApp template:</strong>
 * Template name {@code founder_birthday_greeting}, language {@code en}.
 * The template body must contain exactly one variable: {@code {{1}}} = founder's full name.
 * The template must be approved in Meta WhatsApp Manager before enabling in production.
 * </p>
 */
@Configuration
public class BirthdayConfig {

    /**
     * Approved Meta WhatsApp template name.
     * Override via {@code WHATSAPP_BIRTHDAY_TEMPLATE_NAME} environment variable.
     */
    @Value("${whatsapp.birthday.template-name:founder_birthday_greeting}")
    private String templateName;

    /**
     * Language code of the approved template.
     * Override via {@code WHATSAPP_BIRTHDAY_TEMPLATE_LANGUAGE} environment variable.
     */
    @Value("${whatsapp.birthday.template-language:en}")
    private String templateLanguage;

    /**
     * Whether the daily birthday scheduler is enabled.
     * Disabled by default until the template is approved and the feature is verified.
     * Override via {@code WHATSAPP_BIRTHDAY_SCHEDULER_ENABLED=true}.
     */
    @Value("${whatsapp.birthday.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Cron expression for the birthday scheduler (seconds minutes hours day month weekday).
     * Default: 09:00 Asia/Kolkata daily.
     * Override via {@code WHATSAPP_BIRTHDAY_SCHEDULER_CRON}.
     */
    @Value("${whatsapp.birthday.scheduler.cron:0 0 9 * * *}")
    private String schedulerCron;

    /**
     * Timezone used for the scheduler and for determining "today" during birthday matching.
     * Override via {@code WHATSAPP_BIRTHDAY_SCHEDULER_TIMEZONE}.
     */
    @Value("${whatsapp.birthday.scheduler.timezone:Asia/Kolkata}")
    private String timezone;

    /**
     * Maximum retry attempts for a failed birthday notification before marking it FAILED permanently.
     * Override via {@code WHATSAPP_BIRTHDAY_RETRY_MAX_ATTEMPTS}.
     */
    @Value("${whatsapp.birthday.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Initial backoff interval in minutes for the first retry.
     * Override via {@code WHATSAPP_BIRTHDAY_RETRY_INTERVAL_MINUTES}.
     */
    @Value("${whatsapp.birthday.retry.initial-interval-minutes:5}")
    private int retryInitialIntervalMinutes;

    /**
     * Backoff multiplier applied exponentially on each retry.
     * Override via {@code WHATSAPP_BIRTHDAY_RETRY_MULTIPLIER}.
     */
    @Value("${whatsapp.birthday.retry.multiplier:2.0}")
    private double retryMultiplier;

    /**
     * Leap-day birthday policy (founder born on Feb 29).
     * FEB_28: greet on Feb 28 in non-leap years (default, consistent with anniversary policy).
     * MARCH_1: greet on Mar 1 in non-leap years.
     * Override via {@code WHATSAPP_BIRTHDAY_LEAP_DAY_POLICY}.
     */
    @Value("${whatsapp.birthday.leap-day-policy:FEB_28}")
    private String leapDayPolicy;

    public enum LeapDayPolicy {
        FEB_28,
        MARCH_1
    }

    public String getTemplateName() {
        return (templateName != null && !templateName.isBlank()) ? templateName.trim() : "founder_birthday_greeting";
    }

    public String getTemplateLanguage() {
        return (templateLanguage != null && !templateLanguage.isBlank()) ? templateLanguage.trim() : "en";
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

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public int getRetryInitialIntervalMinutes() {
        return retryInitialIntervalMinutes;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public LeapDayPolicy getLeapDayPolicy() {
        if ("MARCH_1".equalsIgnoreCase(leapDayPolicy)) {
            return LeapDayPolicy.MARCH_1;
        }
        return LeapDayPolicy.FEB_28;
    }
}
