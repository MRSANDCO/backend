package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Founder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Founder} documents.
 */
@Repository
public interface FounderRepository extends MongoRepository<Founder, String> {

    /** All founders linked to a company client, regardless of active state. */
    List<Founder> findByCompanyClientId(String companyClientId);

    /** Active founders linked to a company client. */
    List<Founder> findByCompanyClientIdAndActiveTrue(String companyClientId);

    /** Count founders linked to a company client. */
    long countByCompanyClientId(String companyClientId);

    /** Count active founders linked to a company client. */
    long countByCompanyClientIdAndActiveTrue(String companyClientId);

    /**
     * Efficient birthday-day scan. Called by the scheduler after pre-filtering by month+day.
     * Returns only active founders whose greeting is enabled and consent has been given.
     */
    List<Founder> findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrueAndWhatsappConsentGivenTrue(
            int birthdayMonth, int birthdayDay);

    List<Founder> findByBirthdayMonthAndBirthdayDayAndActiveTrueAndBirthdayGreetingEnabledTrue(
            int birthdayMonth, int birthdayDay);

    List<Founder> findByBirthdayMonthAndBirthdayDayAndActiveTrue(
            int birthdayMonth, int birthdayDay);

    /**
     * Active founders for a given month (used in admin upcoming-birthdays view).
     */
    List<Founder> findByBirthdayMonthAndActiveTrue(int birthdayMonth);

    Optional<Founder> findByCompanyClientIdAndWhatsappNumber(String companyClientId, String whatsappNumber);

    boolean existsByCompanyClientIdAndWhatsappNumber(String companyClientId, String whatsappNumber);
}
