package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.AttendanceStatus;
import com.mrs.ca.backend.Services.AttendanceService;
import com.mrs.ca.backend.dto.AdminAttendanceUpdateRequest;
import com.mrs.ca.backend.dto.AttendanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    public AdminAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * Admin view all employee attendance records with filters.
     */
    @GetMapping
    public ResponseEntity<Page<AttendanceResponse>> getAllAttendance(
            @RequestParam(value = "employeeId", required = false) String employeeId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "status", required = false) AttendanceStatus status,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<AttendanceResponse> records = attendanceService.getAdminAttendanceList(
                employeeId, date, status, startDate, endDate, month, year, page, size
        );
        return ResponseEntity.ok(records);
    }

    /**
     * Admin updates/corrects an employee attendance record.
     */
    @PutMapping("/{attendanceId}")
    public ResponseEntity<?> updateAttendance(
            @PathVariable String attendanceId,
            @RequestBody AdminAttendanceUpdateRequest request
    ) {
        try {
            AttendanceResponse updated = attendanceService.updateAttendanceByAdmin(attendanceId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Attendance record updated successfully",
                    "attendance", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Admin deletes an incorrect attendance record.
     */
    @DeleteMapping("/{attendanceId}")
    public ResponseEntity<?> deleteAttendance(@PathVariable String attendanceId) {
        try {
            attendanceService.deleteAttendanceByAdmin(attendanceId);
            return ResponseEntity.ok(Map.of(
                    "message", "Attendance record deleted successfully",
                    "attendanceId", attendanceId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
