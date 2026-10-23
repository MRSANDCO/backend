package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.AnniversaryConfig;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

/**
 * Dedicated service for annual anniversary date matching, time-zone handling, and leap-day policy resolution.
 */
@Service
public class AnniversaryMatchingService {

    private final AnniversaryConfig anniversaryConfig;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AnniversaryMatchingService(AnniversaryConfig anniversaryConfig) {
        this.anniversaryConfig = anniversaryConfig;
        this.clock = Clock.system(anniversaryConfig.getZoneId());
    }

    public AnniversaryMatchingService(AnniversaryConfig anniversaryConfig, Clock clock) {
        this.anniversaryConfig = anniversaryConfig;
        this.clock = clock;
    }

    /**
     * Get the current business date in the configured time zone (Asia/Kolkata).
     */
    public LocalDate getCurrentBusinessDate() {
        return LocalDate.now(clock.withZone(anniversaryConfig.getZoneId()));
    }

    /**
     * Checks if a client's company formation date matches the specified business date.
     *
     * @param formationDate the original company formation date
     * @param targetDate    the business date being evaluated
     * @return true if targetDate is an annual anniversary of formationDate
     */
    public boolean isAnniversaryToday(LocalDate formationDate, LocalDate targetDate) {
        if (formationDate == null || targetDate == null) {
            return false;
        }

        // Company cannot celebrate anniversary before or on the very day of its formation
        if (targetDate.isBefore(formationDate) || targetDate.isEqual(formationDate)) {
            return false;
        }

        // Standard matching: same month and same day of month
        if (formationDate.getMonth() == targetDate.getMonth() &&
                formationDate.getDayOfMonth() == targetDate.getDayOfMonth()) {
            return true;
        }

        // Leap-day handling: company was formed on February 29
        if (formationDate.getMonth() == Month.FEBRUARY && formationDate.getDayOfMonth() == 29) {
            boolean isTargetLeapYear = Year.isLeap(targetDate.getYear());
            if (!isTargetLeapYear) {
                // In non-leap years, evaluate against configured policy
                AnniversaryConfig.LeapDayPolicy policy = anniversaryConfig.getLeapDayPolicy();
                if (policy == AnniversaryConfig.LeapDayPolicy.FEB_28) {
                    return targetDate.getMonth() == Month.FEBRUARY && targetDate.getDayOfMonth() == 28;
                } else if (policy == AnniversaryConfig.LeapDayPolicy.MARCH_1) {
                    return targetDate.getMonth() == Month.MARCH && targetDate.getDayOfMonth() == 1;
                }
            }
        }

        return false;
    }

    /**
     * Calculates the number of completed years of operation.
     */
    public int calculateYearsCompleted(LocalDate formationDate, LocalDate targetDate) {
        if (formationDate == null || targetDate == null || targetDate.isBefore(formationDate)) {
            return 0;
        }
        return targetDate.getYear() - formationDate.getYear();
    }
}
