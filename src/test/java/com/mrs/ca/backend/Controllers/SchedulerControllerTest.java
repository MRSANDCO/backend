package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Services.AnniversaryMatchingService;
import com.mrs.ca.backend.Services.AnniversarySchedulerService;
import com.mrs.ca.backend.Services.BirthdaySchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    @Mock
    private AnniversarySchedulerService anniversarySchedulerService;

    @Mock
    private BirthdaySchedulerService birthdaySchedulerService;

    @Mock
    private AnniversaryMatchingService anniversaryMatchingService;

    private SchedulerController controller;
    private final LocalDate today = LocalDate.of(2026, 10, 10);
    private final String secret = "mrsco_scheduler_secret_2026";

    @BeforeEach
    void setUp() {
        controller = new SchedulerController(
                anniversarySchedulerService,
                birthdaySchedulerService,
                anniversaryMatchingService
        );
        ReflectionTestUtils.setField(controller, "schedulerSecret", secret);
        lenient().when(anniversaryMatchingService.getCurrentBusinessDate()).thenReturn(today);
    }

    @Test
    void runDailyGreetings_unauthorizedWithoutSecret_returns401() {
        ResponseEntity<?> response = controller.runDailyGreetings(null, null, null, "ALL", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(anniversarySchedulerService, birthdaySchedulerService);
    }

    @Test
    void runDailyGreetings_unauthorizedWithInvalidSecret_returns401() {
        ResponseEntity<?> response = controller.runDailyGreetings("wrong_secret", null, null, "ALL", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(anniversarySchedulerService, birthdaySchedulerService);
    }

    @Test
    void runDailyGreetings_authorizedWithHeaderSecret_triggersBothSchedulers() {
        AnniversarySchedulerService.ExecutionSummary annSummary = new AnniversarySchedulerService.ExecutionSummary(today);
        BirthdaySchedulerService.ExecutionSummary bdaySummary = new BirthdaySchedulerService.ExecutionSummary(today);

        when(anniversarySchedulerService.executeAnniversaryJobForDate(eq(today), any())).thenReturn(annSummary);
        when(birthdaySchedulerService.executeBirthdayJobForDate(eq(today), any())).thenReturn(bdaySummary);

        ResponseEntity<?> response = controller.runDailyGreetings(secret, null, null, "ALL", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("anniversarySummary")).isNotNull();
        assertThat(body.get("birthdaySummary")).isNotNull();
        verify(anniversarySchedulerService).executeAnniversaryJobForDate(eq(today), any());
        verify(birthdaySchedulerService).executeBirthdayJobForDate(eq(today), any());
    }

    @Test
    void runDailyGreetings_withQuerySecret_triggersWorkflows() {
        AnniversarySchedulerService.ExecutionSummary annSummary = new AnniversarySchedulerService.ExecutionSummary(today);
        BirthdaySchedulerService.ExecutionSummary bdaySummary = new BirthdaySchedulerService.ExecutionSummary(today);

        when(anniversarySchedulerService.executeAnniversaryJobForDate(eq(today), any())).thenReturn(annSummary);
        when(birthdaySchedulerService.executeBirthdayJobForDate(eq(today), any())).thenReturn(bdaySummary);

        ResponseEntity<?> response = controller.runDailyGreetings(null, secret, null, "ALL", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(anniversarySchedulerService).executeAnniversaryJobForDate(eq(today), any());
        verify(birthdaySchedulerService).executeBirthdayJobForDate(eq(today), any());
    }

    @Test
    void runDailyGreetings_anniversaryOnly() {
        AnniversarySchedulerService.ExecutionSummary annSummary = new AnniversarySchedulerService.ExecutionSummary(today);
        when(anniversarySchedulerService.executeAnniversaryJobForDate(eq(today), any())).thenReturn(annSummary);

        ResponseEntity<?> response = controller.runDailyGreetings(secret, null, null, "ANNIVERSARY", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(anniversarySchedulerService).executeAnniversaryJobForDate(eq(today), any());
        verifyNoInteractions(birthdaySchedulerService);
    }

    @Test
    void runDailyGreetings_birthdayOnly() {
        BirthdaySchedulerService.ExecutionSummary bdaySummary = new BirthdaySchedulerService.ExecutionSummary(today);
        when(birthdaySchedulerService.executeBirthdayJobForDate(eq(today), any())).thenReturn(bdaySummary);

        ResponseEntity<?> response = controller.runDailyGreetings(secret, null, null, "BIRTHDAY", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(birthdaySchedulerService).executeBirthdayJobForDate(eq(today), any());
        verifyNoInteractions(anniversarySchedulerService);
    }

    @Test
    void getStatus_authorized_returnsStatusMap() {
        when(anniversarySchedulerService.getLastExecutionStatus()).thenReturn("COMPLETED_SUCCESSFULLY");
        when(birthdaySchedulerService.getLastExecutionStatus()).thenReturn("COMPLETED_SUCCESSFULLY");

        ResponseEntity<?> response = controller.getStatus(secret, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("anniversary");
        assertThat(body).containsKey("birthday");
    }
}
