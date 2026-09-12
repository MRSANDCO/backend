package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.AttendanceService;
import com.mrs.ca.backend.dto.AttendanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employee/attendance")
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;

    public EmployeeAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    private String getAuthenticatedEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new SecurityException("Unauthenticated user");
        }
        return auth.getName();
    }

    /**
     * Employee marks check-in attendance for today.
     */
    @PostMapping({"/check-in", "/mark", "/mark-present"})
    public ResponseEntity<?> checkIn() {
        String employeeId = getAuthenticatedEmployeeId();
        try {
            AttendanceResponse response = attendanceService.checkIn(employeeId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Attendance marked successfully",
                    "attendance", response
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get today's attendance status for authenticated employee.
     */
    @GetMapping({"/today", "/status"})
    public ResponseEntity<?> getTodayAttendance() {
        String employeeId = getAuthenticatedEmployeeId();
        LocalDate today = LocalDate.now(attendanceService.getZoneId());
        String formattedDate = today.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

        Optional<AttendanceResponse> todayRecord = attendanceService.getTodayAttendance(employeeId);
        if (todayRecord.isPresent()) {
            AttendanceResponse att = todayRecord.get();
            return ResponseEntity.ok(Map.of(
                    "marked", true,
                    "todayDate", formattedDate,
                    "status", att.getStatus().name(),
                    "attendance", att
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "marked", false,
                    "todayDate", formattedDate,
                    "status", "Not Marked",
                    "attendance", Map.of()
            ));
        }
    }

    /**
     * Get attendance history for authenticated employee.
     */
    @GetMapping("/history")
    public ResponseEntity<?> getAttendanceHistory() {
        String employeeId = getAuthenticatedEmployeeId();
        List<AttendanceResponse> history = attendanceService.getEmployeeAttendanceHistory(employeeId);
        return ResponseEntity.ok(history);
    }
}
