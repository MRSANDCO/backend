package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnniversaryMatchingServiceTest {

    @Mock
    private AnniversaryConfig anniversaryConfig;

    private AnniversaryMatchingService matchingService;

    @BeforeEach
    void setUp() {
        lenient().when(anniversaryConfig.getZoneId()).thenReturn(ZoneId.of("Asia/Kolkata"));
        lenient().when(anniversaryConfig.getLeapDayPolicy()).thenReturn(AnniversaryConfig.LeapDayPolicy.FEB_28);
        matchingService = new AnniversaryMatchingService(anniversaryConfig);
    }

    @Test
    void testStandardAnniversaryMatching() {
        LocalDate formationDate = LocalDate.of(2019, 10, 10);

        // Day before
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 10, 9))).isFalse();

        // Exact anniversary day
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 10, 10))).isTrue();

        // Day after
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 10, 11))).isFalse();

        // Repeats next year
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2027, 10, 10))).isTrue();
    }

    @Test
    void testCannotCelebrateOnDayOfFormationOrBefore() {
        LocalDate formationDate = LocalDate.of(2026, 10, 10);

        // Day of formation
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 10, 10))).isFalse();

        // Before formation date
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2025, 10, 10))).isFalse();
    }

    @Test
    void testMonthBoundaryChanges() {
        LocalDate formationDate = LocalDate.of(2020, 1, 31);

        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 1, 31))).isTrue();
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2026, 2, 1))).isFalse();
    }

    @Test
    void testLeapYearDateInLeapYear() {
        // Formed on leap day: Feb 29, 2020
        LocalDate formationDate = LocalDate.of(2020, 2, 29);

        // In 2024 (a leap year), Feb 29 is the anniversary
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2024, 2, 29))).isTrue();
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2024, 2, 28))).isFalse();
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2024, 3, 1))).isFalse();
    }

    @Test
    void testLeapYearDateInNonLeapYear_Feb28Policy() {
        when(anniversaryConfig.getLeapDayPolicy()).thenReturn(AnniversaryConfig.LeapDayPolicy.FEB_28);
        LocalDate formationDate = LocalDate.of(2020, 2, 29);

        // 2025 is not a leap year
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2025, 2, 28))).isTrue();
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2025, 3, 1))).isFalse();
    }

    @Test
    void testLeapYearDateInNonLeapYear_March1Policy() {
        when(anniversaryConfig.getLeapDayPolicy()).thenReturn(AnniversaryConfig.LeapDayPolicy.MARCH_1);
        LocalDate formationDate = LocalDate.of(2020, 2, 29);

        // 2025 is not a leap year; policy is March 1
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2025, 3, 1))).isTrue();
        assertThat(matchingService.isAnniversaryToday(formationDate, LocalDate.of(2025, 2, 28))).isFalse();
    }

    @Test
    void testCalculateYearsCompleted() {
        LocalDate formationDate = LocalDate.of(2019, 10, 10);
        assertThat(matchingService.calculateYearsCompleted(formationDate, LocalDate.of(2026, 10, 10))).isEqualTo(7);
        assertThat(matchingService.calculateYearsCompleted(formationDate, LocalDate.of(2020, 10, 10))).isEqualTo(1);
        assertThat(matchingService.calculateYearsCompleted(null, LocalDate.of(2026, 10, 10))).isEqualTo(0);
    }
}
