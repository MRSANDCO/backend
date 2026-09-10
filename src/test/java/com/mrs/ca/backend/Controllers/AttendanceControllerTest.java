package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Attendance;
import com.mrs.ca.backend.Models.AttendanceStatus;
import com.mrs.ca.backend.Repositories.AttendanceRepository;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import com.mrs.ca.backend.Services.AttendanceService;
import com.mrs.ca.backend.Services.SequenceGeneratorService;
import com.mrs.ca.backend.dto.AdminAttendanceUpdateRequest;
import com.mrs.ca.backend.dto.AttendanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private SequenceGeneratorService sequenceGeneratorService;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private AttendanceService attendanceService;
    private EmployeeAttendanceController employeeAttendanceController;
    private AdminAttendanceController adminAttendanceController;

    private static final String EMP_ID = "EMP1001";
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        employeeAttendanceController = new EmployeeAttendanceController(attendanceService);
        adminAttendanceController = new AdminAttendanceController(attendanceService);
    }

    private void mockSecurityContext(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Employee check-in success")
    void checkIn_success() {
        mockSecurityContext(EMP_ID);
        LocalDate today = LocalDate.now(IST_ZONE);

        when(attendanceRepository.findByEmployeeIdAndAttendanceDate(EMP_ID, today)).thenReturn(Optional.empty());
        when(sequenceGeneratorService.generateNextAttendanceId()).thenReturn("ATT1001");
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = employeeAttendanceController.checkIn();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("Attendance marked successfully");
        AttendanceResponse attResp = (AttendanceResponse) body.get("attendance");
        assertThat(attResp.getAttendanceId()).isEqualTo("ATT1001");
        assertThat(attResp.getEmployeeId()).isEqualTo(EMP_ID);
        assertThat(attResp.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("Employee check-in duplicate prevention throws Conflict 409")
    void checkIn_duplicate_throwsConflict() {
        mockSecurityContext(EMP_ID);
        LocalDate today = LocalDate.now(IST_ZONE);
        Attendance existing = new Attendance("ATT1001", EMP_ID, "Alice", today, LocalTime.now(IST_ZONE), AttendanceStatus.PRESENT);

        when(attendanceRepository.findByEmployeeIdAndAttendanceDate(EMP_ID, today)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = employeeAttendanceController.checkIn();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Attendance has already been marked for today.");
    }

    @Test
    @DisplayName("Employee check-in handles DB DuplicateKeyException safely (race condition)")
    void checkIn_raceCondition_handlesDuplicateKeyException() {
        mockSecurityContext(EMP_ID);
        LocalDate today = LocalDate.now(IST_ZONE);

        when(attendanceRepository.findByEmployeeIdAndAttendanceDate(EMP_ID, today)).thenReturn(Optional.empty());
        when(sequenceGeneratorService.generateNextAttendanceId()).thenReturn("ATT1001");
        when(attendanceRepository.save(any(Attendance.class))).thenThrow(new DuplicateKeyException("Duplicate key error"));

        ResponseEntity<?> response = employeeAttendanceController.checkIn();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Attendance has already been marked for today.");
    }

    @Test
    @DisplayName("Get today's attendance status when not marked")
    void getTodayAttendance_notMarked() {
        mockSecurityContext(EMP_ID);
        LocalDate today = LocalDate.now(IST_ZONE);

        when(attendanceRepository.findByEmployeeIdAndAttendanceDate(EMP_ID, today)).thenReturn(Optional.empty());

        ResponseEntity<?> response = employeeAttendanceController.getTodayAttendance();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("marked")).isEqualTo(false);
        assertThat(body.get("status")).isEqualTo("Not Marked");
    }

    @Test
    @DisplayName("Get today's attendance status when marked")
    void getTodayAttendance_marked() {
        mockSecurityContext(EMP_ID);
        LocalDate today = LocalDate.now(IST_ZONE);
        Attendance existing = new Attendance("ATT1001", EMP_ID, "Alice", today, LocalTime.of(9, 42), AttendanceStatus.PRESENT);

        when(attendanceRepository.findByEmployeeIdAndAttendanceDate(EMP_ID, today)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = employeeAttendanceController.getTodayAttendance();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("marked")).isEqualTo(true);
        assertThat(body.get("status")).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("Employee gets attendance history")
    void getAttendanceHistory_success() {
        mockSecurityContext(EMP_ID);
        Attendance att1 = new Attendance("ATT1001", EMP_ID, "Alice", LocalDate.now(IST_ZONE), LocalTime.of(9, 42), AttendanceStatus.PRESENT);
        Attendance att2 = new Attendance("ATT1000", EMP_ID, "Alice", LocalDate.now(IST_ZONE).minusDays(1), LocalTime.of(9, 35), AttendanceStatus.PRESENT);

        when(attendanceRepository.findByEmployeeIdOrderByAttendanceDateDesc(EMP_ID)).thenReturn(List.of(att1, att2));

        ResponseEntity<?> response = employeeAttendanceController.getAttendanceHistory();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> history = (List<?>) response.getBody();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Admin gets filtered attendance list")
    void adminGetAllAttendance_success() {
        Attendance att = new Attendance("ATT1001", EMP_ID, "Alice", LocalDate.now(IST_ZONE), LocalTime.of(9, 42), AttendanceStatus.PRESENT);
        Page<Attendance> page = new PageImpl<>(List.of(att));

        when(attendanceRepository.findFilteredAttendance(eq(EMP_ID), any(), any(), any(), any(), any())).thenReturn(page);

        ResponseEntity<Page<AttendanceResponse>> response = adminAttendanceController.getAllAttendance(
                EMP_ID, null, null, null, null, null, null, 0, 20
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Admin updates attendance record")
    void adminUpdateAttendance_success() {
        Attendance att = new Attendance("ATT1001", EMP_ID, "Alice", LocalDate.now(IST_ZONE), LocalTime.of(9, 42), AttendanceStatus.PRESENT);
        when(attendanceRepository.findByAttendanceId("ATT1001")).thenReturn(Optional.of(att));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminAttendanceUpdateRequest req = new AdminAttendanceUpdateRequest();
        req.setStatus(AttendanceStatus.LATE);
        req.setRemarks("Delayed due to traffic");

        ResponseEntity<?> response = adminAttendanceController.updateAttendance("ATT1001", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("Attendance record updated successfully");
    }

    @Test
    @DisplayName("Admin deletes attendance record")
    void adminDeleteAttendance_success() {
        Attendance att = new Attendance("ATT1001", EMP_ID, "Alice", LocalDate.now(IST_ZONE), LocalTime.of(9, 42), AttendanceStatus.PRESENT);
        when(attendanceRepository.findByAttendanceId("ATT1001")).thenReturn(Optional.of(att));

        ResponseEntity<?> response = adminAttendanceController.deleteAttendance("ATT1001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(attendanceRepository).delete(att);
    }
}
