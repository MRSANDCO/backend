package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnniversarySchedulerServiceTest {

    @Mock
    private AnniversaryConfig anniversaryConfig;

    @Mock
    private AnniversaryMatchingService matchingService;

    @Mock
    private AnniversaryGreetingService greetingService;

    @Mock
    private UserRepository userRepository;

    private AnniversarySchedulerService schedulerService;

    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        schedulerService = new AnniversarySchedulerService(
                anniversaryConfig, matchingService, greetingService, userRepository);
    }

    @Test
    @DisplayName("Scheduler processes COMPANY clients and excludes INDIVIDUAL and UNVERIFIED clients")
    void testExecuteAnniversaryJobForDate_FilteringByClientType() {
        // 1. Eligible COMPANY client
        User companyClient = new User("c_comp", "pass", "Company User", "comp@test.com", "admin");
        companyClient.setClientType(ClientType.COMPANY);
        companyClient.setFormationDate(LocalDate.of(2018, 10, 10));

        // 2. INDIVIDUAL client with anniversary matching today - must be skipped
        User individualClient = new User("c_ind", "pass", "Individual User", "ind@test.com", "admin");
        individualClient.setClientType(ClientType.INDIVIDUAL);
        individualClient.setFormationDate(LocalDate.of(2018, 10, 10));

        // 3. Unverified client (clientType is null) with anniversary matching today - must be skipped
        User unverifiedClient = new User("c_unv", "pass", "Unverified User", "unv@test.com", "admin");
        unverifiedClient.setClientType(null);
        unverifiedClient.setFormationDate(LocalDate.of(2018, 10, 10));

        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(companyClient, individualClient, unverifiedClient)));

        when(matchingService.isAnniversaryToday(companyClient.getFormationDate(), today)).thenReturn(true);

        AnniversaryNotificationLog log = new AnniversaryNotificationLog(
                "c_comp", "uid1", "Acme", "Contact", "9876543210", 2026, today);
        when(greetingService.processClientGreeting(eq(companyClient), eq(today), eq(false), eq("SCHEDULER")))
                .thenReturn(Optional.of(log));

        var summary = schedulerService.executeAnniversaryJobForDate(today, "SCHEDULER");

        assertThat(summary.getTotalClientsScanned()).isEqualTo(3);
        assertThat(summary.getEligibleToday()).isEqualTo(1);
        assertThat(summary.getProcessedSuccessfully()).isEqualTo(1);
        assertThat(summary.getFailedOrIneligible()).isEqualTo(0);

        // Verify greetingService was ONLY called for COMPANY client
        verify(greetingService, times(1)).processClientGreeting(eq(companyClient), eq(today), eq(false), any());
        verify(greetingService, never()).processClientGreeting(eq(individualClient), any(), anyBoolean(), any());
        verify(greetingService, never()).processClientGreeting(eq(unverifiedClient), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Daily scheduled job skips execution when scheduler is disabled via config")
    void testExecuteDailyAnniversaryJob_Disabled() {
        when(anniversaryConfig.isSchedulerEnabled()).thenReturn(false);

        schedulerService.executeDailyAnniversaryJob();

        verify(userRepository, never()).findAll(any(Pageable.class));
        verify(greetingService, never()).processClientGreeting(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Retry job triggers retryPendingGreetings on greetingService")
    void testExecutePendingRetriesJob() {
        when(anniversaryConfig.isSchedulerEnabled()).thenReturn(true);
        when(greetingService.retryPendingGreetings()).thenReturn(2);

        schedulerService.executePendingRetriesJob();

        verify(greetingService, times(1)).retryPendingGreetings();
    }
}
