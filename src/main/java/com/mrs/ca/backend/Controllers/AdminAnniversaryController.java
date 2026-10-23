package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Administrative endpoints for managing company formation anniversary greetings.
 * Access is restricted to users with ROLE_ADMIN via SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/anniversary")
public class AdminAnniversaryController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnniversaryController.class);

    private final UserRepository userRepository;
    private final AnniversaryNotificationLogRepository logRepository;
    private final AnniversaryMatchingService matchingService;
    private final AnniversaryGreetingService greetingService;
    private final AnniversarySchedulerService schedulerService;
    private final WhatsAppService whatsAppService;
    private final AnniversaryConfig anniversaryConfig;

    public AdminAnniversaryController(UserRepository userRepository,
                                    AnniversaryNotificationLogRepository logRepository,
                                    AnniversaryMatchingService matchingService,
                                    AnniversaryGreetingService greetingService,
                                    AnniversarySchedulerService schedulerService,
                                    WhatsAppService whatsAppService,
                                    AnniversaryConfig anniversaryConfig) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.matchingService = matchingService;
        this.greetingService = greetingService;
        this.schedulerService = schedulerService;
        this.whatsAppService = whatsAppService;
        this.anniversaryConfig = anniversaryConfig;
    }

    /**
     * Get all clients whose anniversary is today.
     * GET /api/admin/anniversary/today
     */
    @GetMapping("/today")
    public ResponseEntity<List<AnniversaryClientDto>> getTodayAnniversaryClients() {
        LocalDate today = matchingService.getCurrentBusinessDate();
        List<User> clientsWithFormationDate = userRepository.findByActiveTrueAndFormationDateIsNotNull();
        List<AnniversaryClientDto> result = new ArrayList<>();

        for (User user : clientsWithFormationDate) {
            if (user.getClientType() == ClientType.COMPANY && matchingService.isAnniversaryToday(user.getFormationDate(), today)) {
                result.add(mapToDto(user, today));
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get clients with anniversaries in the upcoming N days.
     * GET /api/admin/anniversary/upcoming?days=7
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<AnniversaryClientDto>> getUpcomingAnniversaries(
            @RequestParam(value = "days", defaultValue = "7") int days) {

        int clampedDays = Math.max(1, Math.min(days, 60));
        LocalDate today = matchingService.getCurrentBusinessDate();
        List<User> clientsWithFormationDate = userRepository.findByActiveTrueAndFormationDateIsNotNull();
        List<AnniversaryClientDto> result = new ArrayList<>();

        for (int i = 1; i <= clampedDays; i++) {
            LocalDate futureDate = today.plusDays(i);
            for (User user : clientsWithFormationDate) {
                if (user.getClientType() == ClientType.COMPANY && matchingService.isAnniversaryToday(user.getFormationDate(), futureDate)) {
                    result.add(mapToDto(user, futureDate));
                }
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve clients whose client type is unresolved/unverified.
     * GET /api/admin/anniversary/unverified
     */
    @GetMapping("/unverified")
    public ResponseEntity<List<AnniversaryClientDto>> getUnverifiedClients() {
        LocalDate today = matchingService.getCurrentBusinessDate();
        List<User> unverified = userRepository.findAll().stream()
                .filter(u -> u.getClientType() == null)
                .toList();
        List<AnniversaryClientDto> dtos = unverified.stream()
                .map(u -> mapToDto(u, today))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieve paginated history of anniversary notifications.
     * GET /api/admin/anniversary/history?page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<Page<AnniversaryNotificationLog>> getHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        int clampedSize = Math.max(1, Math.min(size, 100));
        Page<AnniversaryNotificationLog> logs = logRepository.findAll(
                PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(logs);
    }

    /**
     * Retrieve status details of an individual notification log.
     * GET /api/admin/anniversary/history/{id}
     */
    @GetMapping("/history/{id}")
    public ResponseEntity<?> getNotificationStatus(@PathVariable String id) {
        Optional<AnniversaryNotificationLog> logOpt = logRepository.findById(id);
        if (logOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Notification log not found"));
        }
        return ResponseEntity.ok(logOpt.get());
    }

    /**
     * Update client anniversary settings (formation date, automation preference, consent).
     * PUT /api/admin/anniversary/clients/{userId}/settings
     */
    @PutMapping("/clients/{userId}/settings")
    public ResponseEntity<?> updateClientAnniversarySettings(
            @PathVariable String userId,
            @RequestBody AnniversarySettingsRequest request) {

        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User '" + userId + "' not found"));
        }

        User user = userOpt.get();

        if (request.getClientType() != null) {
            user.setClientType(request.getClientType());
            if (request.getClientType() == ClientType.INDIVIDUAL) {
                user.setAnniversaryAutomationEnabled(false);
            }
        }
        if (request.getFormationDate() != null) {
            user.setFormationDate(request.getFormationDate());
        }
        if (request.getCompanyName() != null) {
            user.setCompanyName(request.getCompanyName().trim());
        }
        if (request.getContactPersonName() != null) {
            user.setContactPersonName(request.getContactPersonName().trim());
        }
        if (request.getAnniversaryAutomationEnabled() != null) {
            if (user.getClientType() == ClientType.INDIVIDUAL && Boolean.TRUE.equals(request.getAnniversaryAutomationEnabled())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Anniversary automation cannot be enabled for INDIVIDUAL clients"));
            }
            user.setAnniversaryAutomationEnabled(request.getAnniversaryAutomationEnabled());
        }
        if (request.getWhatsappConsentGiven() != null) {
            user.setWhatsappConsentGiven(request.getWhatsappConsentGiven());
            if (request.getWhatsappConsentGiven()) {
                user.setWhatsappConsentDate(LocalDateTime.now());
            } else {
                user.setWhatsappConsentDate(null);
            }
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("[ADMIN-ANNIVERSARY] Updated anniversary preferences for client '{}'", userId);

        return ResponseEntity.ok(Map.of(
                "message", "Anniversary settings updated successfully",
                "client", mapToDto(user, matchingService.getCurrentBusinessDate())
        ));
    }

    /**
     * Send a test anniversary template message to an administrator-specified phone number.
     * POST /api/admin/anniversary/test-send
     */
    @PostMapping("/test-send")
    public ResponseEntity<?> sendTestGreeting(@RequestBody AnniversaryTestSendRequest request) {
        if (request.getRecipientPhone() == null || request.getRecipientPhone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "recipientPhone is required"));
        }

        String templateName = request.getTemplateName() != null && !request.getTemplateName().isBlank()
                ? request.getTemplateName()
                : anniversaryConfig.getTemplateName();

        String contactPerson = (request.getContactPersonName() != null && !request.getContactPersonName().isBlank())
                ? request.getContactPersonName().trim()
                : anniversaryConfig.getFallbackContactName();
        String companyName = (request.getCompanyName() != null && !request.getCompanyName().isBlank())
                ? request.getCompanyName().trim()
                : anniversaryConfig.getFallbackCompanyName();

        WhatsAppSendResult result = whatsAppService.sendTemplateMessage(
                request.getRecipientPhone(),
                templateName,
                anniversaryConfig.getTemplateLanguage(),
                List.of(contactPerson, companyName)
        );

        if (result.isAccepted()) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "metaMessageId", result.getMetaMessageId(),
                    "message", "Test anniversary template message accepted by Meta WhatsApp API"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "FAILED",
                    "errorCode", result.getErrorCode() != null ? result.getErrorCode() : "UNKNOWN",
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error",
                    "isRetryable", result.isRetryable(),
                    "isAmbiguous", result.isAmbiguous()
            ));
        }
    }

    /**
     * Manually trigger an anniversary greeting for a specific client.
     * POST /api/admin/anniversary/trigger/{userId}
     */
    @PostMapping("/trigger/{userId}")
    public ResponseEntity<?> manuallyTriggerGreeting(@PathVariable String userId, Authentication auth) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Client '" + userId + "' not found"));
        }

        User user = userOpt.get();
        LocalDate today = matchingService.getCurrentBusinessDate();
        String triggeredBy = (auth != null && auth.getName() != null) ? auth.getName() : "ADMIN_MANUAL";

        Optional<AnniversaryNotificationLog> logResult = greetingService.processClientGreeting(
                user, today, true, triggeredBy);

        if (logResult.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "message", "Greeting processed successfully",
                    "notificationLog", logResult.get()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Client is not eligible today or greeting was already processed for this anniversary year"
            ));
        }
    }

    /**
     * On-demand trigger for running today's scheduler job immediately.
     * POST /api/admin/anniversary/scheduler/trigger-now
     */
    @PostMapping("/scheduler/trigger-now")
    public ResponseEntity<?> triggerSchedulerNow(Authentication auth) {
        String adminName = (auth != null && auth.getName() != null) ? auth.getName() : "ADMIN_TRIGGER";
        LocalDate today = matchingService.getCurrentBusinessDate();

        AnniversarySchedulerService.ExecutionSummary summary = schedulerService.executeAnniversaryJobForDate(
                today, adminName);

        return ResponseEntity.ok(Map.of(
                "message", "Anniversary scheduler completed",
                "summary", summary
        ));
    }

    private AnniversaryClientDto mapToDto(User user, LocalDate referenceDate) {
        AnniversaryClientDto dto = new AnniversaryClientDto();
        dto.setUserId(user.getUserId());
        dto.setFullName(user.getFullName());
        dto.setClientType(user.getClientType());
        dto.setCompanyName(user.getEffectiveCompanyName());
        dto.setContactPersonName(user.getEffectiveContactPersonName());
        dto.setPhone(user.getPhone());
        dto.setFormationDate(user.getFormationDate());
        dto.setAnniversaryAutomationEnabled(user.isAnniversaryAutomationEnabled());
        dto.setWhatsappConsentGiven(user.isWhatsappConsentGiven());
        dto.setYearsCompleted(matchingService.calculateYearsCompleted(user.getFormationDate(), referenceDate));
        dto.setAnniversaryToday(matchingService.isAnniversaryToday(user.getFormationDate(), referenceDate));

        Optional<AnniversaryNotificationLog> logOpt = logRepository.findByClientIdAndAnniversaryYearAndNotificationType(
                user.getUserId(), referenceDate.getYear(), AnniversaryNotificationType.COMPANY_FORMATION_ANNIVERSARY);

        logOpt.ifPresent(log -> dto.setLatestNotificationStatus(log.getStatus().name()));
        return dto;
    }
}
