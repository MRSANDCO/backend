package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin REST API for managing founder birthday greetings.
 * All endpoints require {@code ROLE_ADMIN} (enforced by {@link com.mrs.ca.backend.Config.SecurityConfig}).
 *
 * <pre>
 * Base path: /api/admin/founders
 *
 * POST   /                             – Create a founder
 * GET    /company/{companyClientId}    – List founders for a company
 * GET    /{founderId}                  – Get a single founder
 * PUT    /{founderId}                  – Update a founder
 * PATCH  /{founderId}/greeting         – Enable/disable birthday greeting
 * PATCH  /{founderId}/active           – Activate / deactivate founder
 * DELETE /{founderId}                  – Delete a founder
 *
 * GET    /birthday/today               – Founders with birthday today
 * GET    /birthday/upcoming?days=7     – Founders with upcoming birthdays
 * POST   /birthday/trigger-now         – Manually run today's birthday job
 * POST   /birthday/trigger/{founderId} – Manually trigger a specific founder's greeting
 * POST   /birthday/test-send           – Send test birthday greeting
 * GET    /birthday/history             – Notification history (all birthday logs)
 * GET    /birthday/history/{id}        – Single notification log entry
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/founders")
public class AdminFounderController {

    private static final Logger log = LoggerFactory.getLogger(AdminFounderController.class);

    private final FounderService founderService;
    private final FounderRepository founderRepository;
    private final BirthdayGreetingService greetingService;
    private final BirthdaySchedulerService schedulerService;
    private final WhatsAppService whatsAppService;
    private final BirthdayConfig birthdayConfig;
    private final AnniversaryNotificationLogRepository logRepository;

    public AdminFounderController(FounderService founderService,
                                  FounderRepository founderRepository,
                                  BirthdayGreetingService greetingService,
                                  BirthdaySchedulerService schedulerService,
                                  WhatsAppService whatsAppService,
                                  BirthdayConfig birthdayConfig,
                                  AnniversaryNotificationLogRepository logRepository) {
        this.founderService = founderService;
        this.founderRepository = founderRepository;
        this.greetingService = greetingService;
        this.schedulerService = schedulerService;
        this.whatsAppService = whatsAppService;
        this.birthdayConfig = birthdayConfig;
        this.logRepository = logRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FOUNDER CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a founder.
     * POST /api/admin/founders
     *
     * <pre>
     * {
     *   "companyClientId": "MRS001",
     *   "fullName": "Ravi Shankar",
     *   "dateOfBirth": "1980-06-15",
     *   "whatsappNumber": "919876543210",
     *   "birthdayGreetingEnabled": true,
     *   "whatsappConsentGiven": true
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<?> createFounder(@RequestBody FounderRequest request) {
        try {
            Founder founder = founderService.createFounder(request);
            log.info("[ADMIN-FOUNDERS] Created founder '{}' (id: {}) for company '{}'",
                    founder.getFullName(), founder.getId(), founder.getCompanyClientId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Founder created successfully",
                    "founder", founder
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all founders for a company (admin view, all states).
     * GET /api/admin/founders/company/{companyClientId}
     */
    @GetMapping("/company/{companyClientId}")
    public ResponseEntity<List<Founder>> getFoundersByCompany(@PathVariable String companyClientId) {
        List<Founder> founders = founderService.getFoundersByCompany(companyClientId);
        return ResponseEntity.ok(founders);
    }

    /**
     * Get a founder by document ID.
     * GET /api/admin/founders/{founderId}
     */
    @GetMapping("/{founderId}")
    public ResponseEntity<?> getFounder(@PathVariable String founderId) {
        Optional<Founder> founderOpt = founderService.findById(founderId);
        if (founderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Founder '" + founderId + "' not found"));
        }
        return ResponseEntity.ok(founderOpt.get());
    }

    /**
     * Update founder details (all fields optional in update; only non-null values applied).
     * PUT /api/admin/founders/{founderId}
     */
    @PutMapping("/{founderId}")
    public ResponseEntity<?> updateFounder(@PathVariable String founderId,
                                           @RequestBody FounderRequest request) {
        try {
            Founder updated = founderService.updateFounder(founderId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Founder updated successfully",
                    "founder", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Enable or disable birthday greeting for a founder.
     * PATCH /api/admin/founders/{founderId}/greeting
     *
     * <pre>{ "enabled": true }</pre>
     */
    @PatchMapping("/{founderId}/greeting")
    public ResponseEntity<?> setBirthdayGreetingEnabled(@PathVariable String founderId,
                                                        @RequestBody Map<String, Object> body) {
        Object enabledVal = body.get("enabled");
        if (!(enabledVal instanceof Boolean)) {
            return ResponseEntity.badRequest().body(Map.of("error", "'enabled' field (boolean) is required"));
        }
        try {
            Founder updated = founderService.setBirthdayGreetingEnabled(founderId, (Boolean) enabledVal);
            return ResponseEntity.ok(Map.of(
                    "message", "Birthday greeting " + (updated.isBirthdayGreetingEnabled() ? "enabled" : "disabled"),
                    "founder", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Activate or deactivate a founder record.
     * PATCH /api/admin/founders/{founderId}/active
     *
     * <pre>{ "active": false }</pre>
     */
    @PatchMapping("/{founderId}/active")
    public ResponseEntity<?> setFounderActive(@PathVariable String founderId,
                                              @RequestBody Map<String, Object> body) {
        Object activeVal = body.get("active");
        if (!(activeVal instanceof Boolean)) {
            return ResponseEntity.badRequest().body(Map.of("error", "'active' field (boolean) is required"));
        }
        try {
            Founder updated = founderService.setActive(founderId, (Boolean) activeVal);
            return ResponseEntity.ok(Map.of(
                    "message", "Founder " + (updated.isActive() ? "activated" : "deactivated"),
                    "founder", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a founder record (notification history retained for audit).
     * DELETE /api/admin/founders/{founderId}
     */
    @DeleteMapping("/{founderId}")
    public ResponseEntity<?> deleteFounder(@PathVariable String founderId) {
        try {
            founderService.deleteFounder(founderId);
            return ResponseEntity.ok(Map.of("message", "Founder '" + founderId + "' deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BIRTHDAY MANAGEMENT VIEWS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * List active founders whose birthday is today.
     * GET /api/admin/founders/birthday/today
     */
    @GetMapping("/birthday/today")
    public ResponseEntity<List<Founder>> getTodayBirthdayFounders() {
        LocalDate today = schedulerService.getCurrentBusinessDate();
        List<Founder> todayBirthdays = founderRepository
                .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(
                        today.getMonthValue(), today.getDayOfMonth());
        return ResponseEntity.ok(todayBirthdays);
    }

    /**
     * List founders with birthdays in the upcoming N days (1–60).
     * GET /api/admin/founders/birthday/upcoming?days=7
     */
    @GetMapping("/birthday/upcoming")
    public ResponseEntity<List<Founder>> getUpcomingBirthdayFounders(
            @RequestParam(defaultValue = "7") int days) {
        int clampedDays = Math.max(1, Math.min(days, 60));
        LocalDate today = schedulerService.getCurrentBusinessDate();
        List<Founder> result = new java.util.ArrayList<>();

        for (int i = 1; i <= clampedDays; i++) {
            LocalDate futureDate = today.plusDays(i);
            List<Founder> dayFounders = founderRepository
                    .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(
                            futureDate.getMonthValue(), futureDate.getDayOfMonth());
            result.addAll(dayFounders);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve birthday notification history (FOUNDER_BIRTHDAY type only).
     * GET /api/admin/founders/birthday/history?page=0&size=20
     */
    @GetMapping("/birthday/history")
    public ResponseEntity<List<AnniversaryNotificationLog>> getBirthdayHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.max(1, Math.min(size, 100));
        int offset = page * clampedSize;
        // Filter by notification type from the shared collection
        List<AnniversaryNotificationLog> allBirthday = logRepository.findAll().stream()
                .filter(l -> l.getNotificationType() == AnniversaryNotificationType.FOUNDER_BIRTHDAY)
                .sorted((a, b) -> b.getCreatedAt() != null && a.getCreatedAt() != null
                        ? b.getCreatedAt().compareTo(a.getCreatedAt()) : 0)
                .skip(offset)
                .limit(clampedSize)
                .toList();
        return ResponseEntity.ok(allBirthday);
    }

    /**
     * Get a single birthday notification log entry.
     * GET /api/admin/founders/birthday/history/{id}
     */
    @GetMapping("/birthday/history/{id}")
    public ResponseEntity<?> getBirthdayHistoryEntry(@PathVariable String id) {
        Optional<AnniversaryNotificationLog> logOpt = logRepository.findById(id);
        if (logOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Notification log '" + id + "' not found"));
        }
        return ResponseEntity.ok(logOpt.get());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SCHEDULER TRIGGERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Manually run today's birthday greeting job immediately.
     * POST /api/admin/founders/birthday/trigger-now
     */
    @PostMapping("/birthday/trigger-now")
    public ResponseEntity<?> triggerBirthdayJobNow(Authentication auth) {
        String adminName = (auth != null && auth.getName() != null) ? auth.getName() : "ADMIN_TRIGGER";
        LocalDate today = schedulerService.getCurrentBusinessDate();
        log.info("[ADMIN-FOUNDERS] Admin '{}' triggered birthday job for {}", adminName, today);

        BirthdaySchedulerService.ExecutionSummary summary =
                schedulerService.executeBirthdayJobForDate(today, adminName);

        return ResponseEntity.ok(Map.of(
                "message", "Birthday scheduler job completed",
                "summary", Map.of(
                        "executionDate", summary.getExecutionDate().toString(),
                        "foundersScanned", summary.getFoundersScanned(),
                        "eligible", summary.getEligible(),
                        "dispatched", summary.getDispatched(),
                        "skippedOrFailed", summary.getSkippedOrFailed(),
                        "durationMillis", summary.getDurationMillis()
                )
        ));
    }

    /**
     * Manually trigger a birthday greeting for a specific founder.
     * POST /api/admin/founders/birthday/trigger/{founderId}
     *
     * <p>Optional query param {@code date=YYYY-MM-DD} overrides the business date
     * (useful for backfill or testing).</p>
     */
    @PostMapping("/birthday/trigger/{founderId}")
    public ResponseEntity<?> triggerBirthdayForFounder(
            @PathVariable String founderId,
            @RequestParam(required = false) String date,
            Authentication auth) {

        Optional<Founder> founderOpt = founderService.findById(founderId);
        if (founderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Founder '" + founderId + "' not found"));
        }

        LocalDate businessDate;
        if (date != null && !date.isBlank()) {
            try {
                businessDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use YYYY-MM-DD."));
            }
        } else {
            businessDate = schedulerService.getCurrentBusinessDate();
        }

        String triggeredBy = (auth != null && auth.getName() != null) ? auth.getName() : "ADMIN_MANUAL";
        Optional<AnniversaryNotificationLog> result =
                greetingService.processFounderBirthday(founderOpt.get(), businessDate, true, triggeredBy);

        if (result.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "message", "Birthday greeting processed successfully",
                    "notificationLog", result.get()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Founder is not eligible or greeting was already processed for this birthday year"
            ));
        }
    }

    /**
     * Send a test birthday template message to any phone number without creating a log record.
     * POST /api/admin/founders/birthday/test-send
     *
     * <pre>
     * {
     *   "recipientPhone": "919876543210",
     *   "founderName": "Ravi Shankar"
     * }
     * </pre>
     */
    @PostMapping("/birthday/test-send")
    public ResponseEntity<?> sendTestBirthdayGreeting(@RequestBody Map<String, String> body) {
        String recipientPhone = body.get("recipientPhone");
        String founderName = body.getOrDefault("founderName", "Valued Founder");

        if (recipientPhone == null || recipientPhone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "recipientPhone is required"));
        }

        String templateName = birthdayConfig.getTemplateName();
        String templateLang = birthdayConfig.getTemplateLanguage();

        log.info("[ADMIN-FOUNDERS] Admin test birthday send to '{}' for founder '{}'", recipientPhone, founderName);

        WhatsAppSendResult result = whatsAppService.sendTemplateMessage(
                recipientPhone, templateName, templateLang, List.of(founderName));

        if (result.isAccepted()) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "metaMessageId", result.getMetaMessageId(),
                    "templateName", templateName,
                    "templateLanguage", templateLang,
                    "message", "Test birthday template accepted by Meta WhatsApp API"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "FAILED",
                    "errorCode", result.getErrorCode() != null ? result.getErrorCode() : "UNKNOWN",
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error",
                    "isRetryable", result.isRetryable(),
                    "isAmbiguous", result.isAmbiguous(),
                    "note", "If error indicates the template is not found or pending approval, " +
                            "submit 'founder_birthday_greeting' for approval in Meta WhatsApp Manager."
            ));
        }
    }
}
