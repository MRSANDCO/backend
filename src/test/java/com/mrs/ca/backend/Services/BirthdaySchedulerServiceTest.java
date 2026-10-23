package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.BirthdayConfig;
import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Repositories.FounderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BirthdaySchedulerService}.
 * Tests birthday matching, scheduler disabled guard, leap-day policy, and duplicate prevention.
 */
@ExtendWith(MockitoExtension.class)
class BirthdaySchedulerServiceTest {

    @Mock private BirthdayConfig birthdayConfig;
    @Mock private BirthdayGreetingService greetingService;
    @Mock private FounderRepository founderRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        lenient().when(birthdayConfig.getZoneId()).thenReturn(IST);
    }

    private BirthdaySchedulerService schedulerWith(LocalDate fixedDate) {
        Clock fixedClock = Clock.fixed(
                fixedDate.atStartOfDay(IST).toInstant(), IST);
        return new BirthdaySchedulerService(birthdayConfig, greetingService, founderRepository, fixedClock);
    }

    private Founder founder(String id, int month, int day) {
        Founder f = new Founder();
        f.setId(id);
        f.setFullName("Test Founder " + id);
        f.setCompanyClientId("MRS001");
        f.setWhatsappNumber("919876543210");
        f.setActive(true);
        f.setBirthdayGreetingEnabled(true);
        f.setWhatsappConsentGiven(true);
        f.setDateOfBirth(LocalDate.of(1980, month, day));
        return f;
    }

    // ─── Scheduler disabled ───────────────────────────────────────────────────────

    @Test
    void executeDailyBirthdayJob_SchedulerDisabled_DoesNothing() {
        when(birthdayConfig.isSchedulerEnabled()).thenReturn(false);
        BirthdaySchedulerService scheduler = schedulerWith(LocalDate.of(2026, 6, 15));

        scheduler.executeDailyBirthdayJob();

        verify(founderRepository, never()).findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(anyInt(), anyInt());
        verify(greetingService, never()).processFounderBirthday(any(), any(), anyBoolean(), any());
    }

    // ─── Normal birthday match ────────────────────────────────────────────────────

    @Test
    void executeBirthdayJobForDate_MatchingFounder_DispatchesGreeting() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        BirthdaySchedulerService scheduler = schedulerWith(today);

        Founder f = founder("f001", 6, 15);
        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(6, 15))
                .thenReturn(List.of(f));

        AnniversaryNotificationLog log = new AnniversaryNotificationLog();
        log.setId("log_001");
        log.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        log.setNotificationType(AnniversaryNotificationType.FOUNDER_BIRTHDAY);

        when(greetingService.processFounderBirthday(eq(f), eq(today), eq(false), eq("SCHEDULER")))
                .thenReturn(Optional.of(log));

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(today, "SCHEDULER");

        assertThat(summary.getFoundersScanned()).isEqualTo(1);
        assertThat(summary.getDispatched()).isEqualTo(1);
        assertThat(summary.getSkippedOrFailed()).isEqualTo(0);
    }

    @Test
    void executeBirthdayJobForDate_NoMatchingFounders_EmptySummary() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        BirthdaySchedulerService scheduler = schedulerWith(today);

        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(6, 15))
                .thenReturn(Collections.emptyList());

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(today, "SCHEDULER");

        assertThat(summary.getFoundersScanned()).isEqualTo(0);
        assertThat(summary.getDispatched()).isEqualTo(0);
        verify(greetingService, never()).processFounderBirthday(any(), any(), anyBoolean(), any());
    }

    // ─── Leap-day: Feb 29 founders in non-leap year (FEB_28 policy) ──────────────

    @Test
    void executeBirthdayJobForDate_Feb28NonLeapYear_AlsoMatchesFeb29Founders() {
        LocalDate feb28_2026 = LocalDate.of(2026, 2, 28); // 2026 is not a leap year
        BirthdaySchedulerService scheduler = schedulerWith(feb28_2026);

        when(birthdayConfig.getLeapDayPolicy()).thenReturn(BirthdayConfig.LeapDayPolicy.FEB_28);

        Founder normalFeb28 = founder("f001", 2, 28);
        Founder leapDayFounder = founder("f002", 2, 29);

        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 28))
                .thenReturn(List.of(normalFeb28));
        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29))
                .thenReturn(List.of(leapDayFounder));

        when(greetingService.processFounderBirthday(any(), any(), anyBoolean(), any()))
                .thenReturn(Optional.empty());

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(feb28_2026, "SCHEDULER");

        // Both the Feb-28 founder and the leap-day (Feb-29) founder should be processed
        assertThat(summary.getFoundersScanned()).isEqualTo(2);
        verify(greetingService, times(2)).processFounderBirthday(any(), any(), anyBoolean(), any());
    }

    @Test
    void executeBirthdayJobForDate_Feb29LeapYear_OnlyMatchesFeb29Founders() {
        LocalDate feb29_2028 = LocalDate.of(2028, 2, 29); // 2028 IS a leap year
        BirthdaySchedulerService scheduler = schedulerWith(feb29_2028);

        Founder feb29Founder = founder("f002", 2, 29);
        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29))
                .thenReturn(List.of(feb29Founder));

        when(greetingService.processFounderBirthday(any(), any(), anyBoolean(), any()))
                .thenReturn(Optional.empty());

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(feb29_2028, "SCHEDULER");

        // Only the direct match, no augmentation (it IS a leap year so Feb-29 is a real date)
        assertThat(summary.getFoundersScanned()).isEqualTo(1);
        // No duplicate Feb-29 augmentation query
        verify(founderRepository, times(1))
                .findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29);
    }

    // ─── Leap-day: Mar 1 policy ───────────────────────────────────────────────────

    @Test
    void executeBirthdayJobForDate_Mar1NonLeapYear_WithMarch1Policy_MatchesFeb29Founders() {
        LocalDate mar1_2026 = LocalDate.of(2026, 3, 1);
        BirthdaySchedulerService scheduler = schedulerWith(mar1_2026);

        when(birthdayConfig.getLeapDayPolicy()).thenReturn(BirthdayConfig.LeapDayPolicy.MARCH_1);

        Founder normalMar1 = founder("f001", 3, 1);
        Founder leapDayFounder = founder("f002", 2, 29);

        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(3, 1))
                .thenReturn(List.of(normalMar1));
        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(2, 29))
                .thenReturn(List.of(leapDayFounder));

        when(greetingService.processFounderBirthday(any(), any(), anyBoolean(), any()))
                .thenReturn(Optional.empty());

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(mar1_2026, "SCHEDULER");

        assertThat(summary.getFoundersScanned()).isEqualTo(2);
        verify(greetingService, times(2)).processFounderBirthday(any(), any(), anyBoolean(), any());
    }

    // ─── Exception handling ───────────────────────────────────────────────────────

    @Test
    void executeBirthdayJobForDate_ExceptionForOneFounder_ContinuesWithOthers() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        BirthdaySchedulerService scheduler = schedulerWith(today);

        Founder f1 = founder("f001", 6, 15);
        Founder f2 = founder("f002", 6, 15);

        when(founderRepository.findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(6, 15))
                .thenReturn(List.of(f1, f2));

        when(greetingService.processFounderBirthday(eq(f1), any(), anyBoolean(), any()))
                .thenThrow(new RuntimeException("Simulated failure for f1"));

        AnniversaryNotificationLog log2 = new AnniversaryNotificationLog();
        log2.setId("log_002");
        log2.setStatus(AnniversaryNotificationStatus.ACCEPTED);
        when(greetingService.processFounderBirthday(eq(f2), any(), anyBoolean(), any()))
                .thenReturn(Optional.of(log2));

        BirthdaySchedulerService.ExecutionSummary summary =
                scheduler.executeBirthdayJobForDate(today, "SCHEDULER");

        assertThat(summary.getFoundersScanned()).isEqualTo(2);
        assertThat(summary.getDispatched()).isEqualTo(1);
        assertThat(summary.getSkippedOrFailed()).isEqualTo(1);
    }

    // ─── getCurrentBusinessDate ───────────────────────────────────────────────────

    @Test
    void getCurrentBusinessDate_ReturnsDateInIST() {
        LocalDate expected = LocalDate.of(2026, 10, 17);
        BirthdaySchedulerService scheduler = schedulerWith(expected);
        assertThat(scheduler.getCurrentBusinessDate()).isEqualTo(expected);
    }
}
