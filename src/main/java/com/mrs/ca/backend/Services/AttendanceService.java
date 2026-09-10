package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.Attendance;
import com.mrs.ca.backend.Models.AttendanceStatus;
import com.mrs.ca.backend.Models.Employee;
import com.mrs.ca.backend.Repositories.AttendanceRepository;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import com.mrs.ca.backend.dto.AdminAttendanceUpdateRequest;
import com.mrs.ca.backend.dto.AttendanceResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SequenceGeneratorService sequenceGeneratorService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EmployeeRepository employeeRepository,
                             SequenceGeneratorService sequenceGeneratorService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
    }

    public ZoneId getZoneId() {
        return IST_ZONE;
    }

    /**
     * Mark daily check-in attendance for employee.
     * Prevents duplicate check-in at both application and database level.
     */
    public AttendanceResponse checkIn(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("Employee ID is required");
        }

        LocalDate today = LocalDate.now(IST_ZONE);
        LocalTime nowTime = LocalTime.now(IST_ZONE);

        Optional<Attendance> existing = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, today);
        if (existing.isPresent()) {
            throw new IllegalStateException("Attendance has already been marked for today.");
        }

        String employeeName = "";
        Optional<Employee> empOpt = employeeRepository.findByEmployeeId(employeeId);
        if (empOpt.isPresent() && empOpt.get().getName() != null) {
            employeeName = empOpt.get().getName();
        }

        String attendanceId = sequenceGeneratorService.generateNextAttendanceId();
        Attendance attendance = new Attendance(
                attendanceId,
                employeeId,
                employeeName,
                today,
                nowTime,
                AttendanceStatus.PRESENT
        );

        try {
            Attendance saved = attendanceRepository.save(attendance);
            return AttendanceResponse.fromEntity(saved);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Attendance has already been marked for today.");
        }
    }

    /**
     * Get today's attendance record for employee.
     */
    public Optional<AttendanceResponse> getTodayAttendance(String employeeId) {
        LocalDate today = LocalDate.now(IST_ZONE);
        return attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, today)
                .map(AttendanceResponse::fromEntity);
    }

    /**
     * Get complete attendance history for employee.
     */
    public List<AttendanceResponse> getEmployeeAttendanceHistory(String employeeId) {
        return attendanceRepository.findByEmployeeIdOrderByAttendanceDateDesc(employeeId)
                .stream()
                .map(AttendanceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Admin view and filter all employee attendance.
     */
    public Page<AttendanceResponse> getAdminAttendanceList(
            String employeeId,
            LocalDate date,
            AttendanceStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Integer month,
            Integer year,
            int page,
            int size
    ) {
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attendance_date", "created_at"));
        
        Page<Attendance> records = attendanceRepository.findFilteredAttendance(
                (employeeId != null && !employeeId.isBlank()) ? employeeId : null,
                date,
                status,
                startDate,
                endDate,
                pageable
        );

        return records.map(AttendanceResponse::fromEntity);
    }

    /**
     * Admin updates/corrects an employee attendance record.
     */
    public AttendanceResponse updateAttendanceByAdmin(String attendanceId, AdminAttendanceUpdateRequest request) {
        Attendance attendance = attendanceRepository.findByAttendanceId(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found with ID: " + attendanceId));

        if (request.getStatus() != null) {
            attendance.setStatus(request.getStatus());
        }
        if (request.getCheckInTime() != null) {
            attendance.setCheckInTime(request.getCheckInTime());
        }
        if (request.getCheckOutTime() != null) {
            attendance.setCheckOutTime(request.getCheckOutTime());
        }
        if (request.getRemarks() != null) {
            attendance.setRemarks(request.getRemarks());
        }

        Attendance saved = attendanceRepository.save(attendance);
        return AttendanceResponse.fromEntity(saved);
    }

    /**
     * Admin deletes an incorrect attendance record.
     */
    public void deleteAttendanceByAdmin(String attendanceId) {
        Attendance attendance = attendanceRepository.findByAttendanceId(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found with ID: " + attendanceId));
        attendanceRepository.delete(attendance);
    }
}
