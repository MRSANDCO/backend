package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.AnniversaryMatchingService;
import com.mrs.ca.backend.Services.AnniversarySchedulerService;
import com.mrs.ca.backend.Services.BirthdaySchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for automated greeting scheduler runs.
 * Supports external triggers (e.g. cron-job.org, Google Cloud Scheduler, AWS EventBridge, GitHub Actions, curl)
 * using a secret token header 'X-Scheduler-Secret' or query parameter 'secret',
 * as well as authenticated administrators.
 */
@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerController.class);

    private final AnniversarySchedulerService anniversarySchedulerService;
    private final BirthdaySchedulerService birthdaySchedulerService;
    private final AnniversaryMatchingService anniversaryMatchingService;

    @Value("${app.scheduler.secret:mrsco_scheduler_secret_2026}")
    private String schedulerSecret;

    public SchedulerController(AnniversarySchedulerService anniversarySchedulerService,
                               BirthdaySchedulerService birthdaySchedulerService,
                               AnniversaryMatchingService anniversaryMatchingService) {
        this.anniversarySchedulerService = anniversarySchedulerService;
        this.birthdaySchedulerService = birthdaySchedulerService;
        this.anniversaryMatchingService = anniversaryMatchingService;
    }

    private boolean isAuthorized(String headerSecret, String querySecret, Authentication auth) {
        if (schedulerSecret != null && !schedulerSecret.isBlank()) {
            if (schedulerSecret.equals(headerSecret) || schedulerSecret.equals(querySecret)) {
                return true;
            }
        }
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    @PostMapping({"/daily-greetings", "/run"})
    public ResponseEntity<?> runDailyGreetings(
            @RequestHeader(value = "X-Scheduler-Secret", required = false) String headerSecret,
            @RequestParam(value = "secret", required = false) String querySecret,
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "type", defaultValue = "ALL") String type,
            Authentication auth) {

        if (!isAuthorized(headerSecret, querySecret, auth)) {
            log.warn("[SCHEDULER-ENDPOINT] Unauthorized scheduler trigger attempt.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Scheduler-Secret header or Admin authentication required"));
        }

        LocalDate businessDate = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr)
                : anniversaryMatchingService.getCurrentBusinessDate();

        String triggeredBy = (auth != null && auth.getName() != null) ? auth.getName() : "EXTERNAL_SCHEDULER";
        log.info("[SCHEDULER-ENDPOINT] Triggering greeting workflows for date: {} [type: {}, triggeredBy: {}]",
                businessDate, type, triggeredBy);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionDate", businessDate.toString());
        response.put("triggeredBy", triggeredBy);
        response.put("type", type);

        boolean runAnniversaries = "ALL".equalsIgnoreCase(type) || "ANNIVERSARY".equalsIgnoreCase(type);
        boolean runBirthdays = "ALL".equalsIgnoreCase(type) || "BIRTHDAY".equalsIgnoreCase(type);

        if (runAnniversaries) {
            AnniversarySchedulerService.ExecutionSummary anniversarySummary =
                    anniversarySchedulerService.executeAnniversaryJobForDate(businessDate, triggeredBy);
            response.put("anniversarySummary", anniversarySummary);
        }

        if (runBirthdays) {
            BirthdaySchedulerService.ExecutionSummary birthdaySummary =
                    birthdaySchedulerService.executeBirthdayJobForDate(businessDate, triggeredBy);
            response.put("birthdaySummary", birthdaySummary);
        }

        response.put("status", "SUCCESS");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(
            @RequestHeader(value = "X-Scheduler-Secret", required = false) String headerSecret,
            @RequestParam(value = "secret", required = false) String querySecret,
            Authentication auth) {

        if (!isAuthorized(headerSecret, querySecret, auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Scheduler-Secret header or Admin authentication required"));
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentBusinessDate", anniversaryMatchingService.getCurrentBusinessDate().toString());

        Map<String, Object> anniversaryStatus = new LinkedHashMap<>();
        anniversaryStatus.put("lastExecutionTime", anniversarySchedulerService.getLastExecutionTime() != null
                ? anniversarySchedulerService.getLastExecutionTime().toString() : "NEVER");
        anniversaryStatus.put("lastExecutionStatus", anniversarySchedulerService.getLastExecutionStatus());
        anniversaryStatus.put("lastExecutionSummary", anniversarySchedulerService.getLastExecutionSummary());
        anniversaryStatus.put("recentErrors", anniversarySchedulerService.getRecentErrors());
        status.put("anniversary", anniversaryStatus);

        Map<String, Object> birthdayStatus = new LinkedHashMap<>();
        birthdayStatus.put("lastExecutionTime", birthdaySchedulerService.getLastExecutionTime() != null
                ? birthdaySchedulerService.getLastExecutionTime().toString() : "NEVER");
        birthdayStatus.put("lastExecutionStatus", birthdaySchedulerService.getLastExecutionStatus());
        birthdayStatus.put("lastExecutionSummary", birthdaySchedulerService.getLastExecutionSummary());
        birthdayStatus.put("recentErrors", birthdaySchedulerService.getRecentErrors());
        status.put("birthday", birthdayStatus);

        return ResponseEntity.ok(status);
    }
}
