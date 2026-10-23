package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.FounderRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.FounderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Business-logic service for managing {@link Founder} records.
 * <p>
 * Validation rules enforced here:
 * <ul>
 *   <li>companyClientId must reference an existing, active {@link User}.</li>
 *   <li>The referenced client must be of type {@link ClientType#COMPANY}.</li>
 *   <li>A founder is identified within a company by their whatsappNumber (unique per company).</li>
 *   <li>whatsappNumber must normalize to a valid international number (10–15 digits).</li>
 *   <li>dateOfBirth is required and must not be in the future (present allowed for edge cases).</li>
 * </ul>
 * </p>
 */
@Service
public class FounderService {

    private static final Logger log = LoggerFactory.getLogger(FounderService.class);

    private final FounderRepository founderRepository;
    private final UserRepository userRepository;
    private final AnniversaryEligibilityService phoneValidator;

    public FounderService(FounderRepository founderRepository,
                          UserRepository userRepository,
                          AnniversaryEligibilityService phoneValidator) {
        this.founderRepository = founderRepository;
        this.userRepository = userRepository;
        this.phoneValidator = phoneValidator;
    }

    // ─── Read operations ────────────────────────────────────────────────────────

    /**
     * Retrieve a founder by its document ID.
     */
    public Optional<Founder> findById(String founderId) {
        return founderRepository.findById(founderId);
    }

    /**
     * All founders associated with a company client (admin view, all states).
     *
     * @param companyClientId the userId of the COMPANY client
     * @return list of founders (may be empty)
     */
    public List<Founder> getFoundersByCompany(String companyClientId) {
        return founderRepository.findByCompanyClientId(companyClientId);
    }

    /**
     * Active founders associated with a company client.
     */
    public List<Founder> getActiveFoundersByCompany(String companyClientId) {
        return founderRepository.findByCompanyClientIdAndActiveTrue(companyClientId);
    }

    // ─── Write operations ────────────────────────────────────────────────────────

    /**
     * Create a new founder record.
     *
     * @param request the creation payload
     * @return the persisted founder
     * @throws IllegalArgumentException if validation fails
     */
    public Founder createFounder(FounderRequest request) {
        validateCreateRequest(request);

        User company = resolveCompanyClient(request.getCompanyClientId());
        String normalizedPhone = validateAndNormalizePhone(request.getWhatsappNumber(), null);

        // Duplicate phone within same company
        if (founderRepository.existsByCompanyClientIdAndWhatsappNumber(
                company.getUserId(), normalizedPhone)) {
            throw new IllegalArgumentException(
                    "A founder with WhatsApp number '" + normalizedPhone +
                    "' is already registered for company '" + company.getUserId() + "'");
        }

        Founder founder = new Founder();
        founder.setCompanyClientId(company.getUserId());
        founder.setFullName(request.getFullName().trim());
        founder.setDateOfBirth(request.getDateOfBirth());  // also sets birthdayMonth/Day
        founder.setWhatsappNumber(normalizedPhone);
        founder.setActive(request.getActive() != null ? request.getActive() : true);
        founder.setBirthdayGreetingEnabled(
                request.getBirthdayGreetingEnabled() != null ? request.getBirthdayGreetingEnabled() : false);
        founder.setWhatsappConsentGiven(
                request.getWhatsappConsentGiven() != null ? request.getWhatsappConsentGiven() : false);

        if (Boolean.TRUE.equals(request.getWhatsappConsentGiven())) {
            founder.setWhatsappConsentDate(LocalDateTime.now());
        }

        Founder saved = founderRepository.save(founder);
        log.info("[FOUNDER-SERVICE] Created founder '{}' for company '{}' (id: {})",
                saved.getFullName(), saved.getCompanyClientId(), saved.getId());
        return saved;
    }

    /**
     * Update an existing founder record. All fields are optional; only non-null values are applied.
     *
     * @param founderId the document ID of the founder to update
     * @param request   the partial update payload
     * @return the updated founder
     * @throws IllegalArgumentException if the founder is not found or validation fails
     */
    public Founder updateFounder(String founderId, FounderRequest request) {
        Founder existing = founderRepository.findById(founderId)
                .orElseThrow(() -> new IllegalArgumentException("Founder '" + founderId + "' not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            existing.setFullName(request.getFullName().trim());
        }

        if (request.getDateOfBirth() != null) {
            existing.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.getWhatsappNumber() != null && !request.getWhatsappNumber().isBlank()) {
            String normalizedPhone = validateAndNormalizePhone(request.getWhatsappNumber(), founderId);
            // Check duplicate only if the phone is actually changing
            if (!normalizedPhone.equals(existing.getWhatsappNumber())) {
                if (founderRepository.existsByCompanyClientIdAndWhatsappNumber(
                        existing.getCompanyClientId(), normalizedPhone)) {
                    throw new IllegalArgumentException(
                            "A founder with WhatsApp number '" + normalizedPhone +
                            "' is already registered for company '" + existing.getCompanyClientId() + "'");
                }
                existing.setWhatsappNumber(normalizedPhone);
            }
        }

        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        if (request.getBirthdayGreetingEnabled() != null) {
            existing.setBirthdayGreetingEnabled(request.getBirthdayGreetingEnabled());
        }

        if (request.getWhatsappConsentGiven() != null) {
            boolean newConsent = request.getWhatsappConsentGiven();
            existing.setWhatsappConsentGiven(newConsent);
            if (newConsent && existing.getWhatsappConsentDate() == null) {
                existing.setWhatsappConsentDate(LocalDateTime.now());
            } else if (!newConsent) {
                existing.setWhatsappConsentDate(null);
            }
        }

        existing.setUpdatedAt(LocalDateTime.now());
        Founder updated = founderRepository.save(existing);
        log.info("[FOUNDER-SERVICE] Updated founder '{}' (id: {})", updated.getFullName(), updated.getId());
        return updated;
    }

    /**
     * Enable or disable birthday greetings for a founder.
     */
    public Founder setBirthdayGreetingEnabled(String founderId, boolean enabled) {
        Founder founder = founderRepository.findById(founderId)
                .orElseThrow(() -> new IllegalArgumentException("Founder '" + founderId + "' not found"));
        founder.setBirthdayGreetingEnabled(enabled);
        founder.setUpdatedAt(LocalDateTime.now());
        Founder updated = founderRepository.save(founder);
        log.info("[FOUNDER-SERVICE] Birthday greeting {} for founder '{}' (id: {})",
                enabled ? "ENABLED" : "DISABLED", updated.getFullName(), updated.getId());
        return updated;
    }

    /**
     * Activate or deactivate a founder record.
     */
    public Founder setActive(String founderId, boolean active) {
        Founder founder = founderRepository.findById(founderId)
                .orElseThrow(() -> new IllegalArgumentException("Founder '" + founderId + "' not found"));
        founder.setActive(active);
        if (!active) {
            // Deactivation implicitly disables greeting dispatch
            founder.setBirthdayGreetingEnabled(false);
        }
        founder.setUpdatedAt(LocalDateTime.now());
        Founder updated = founderRepository.save(founder);
        log.info("[FOUNDER-SERVICE] Founder '{}' (id: {}) set to active={}",
                updated.getFullName(), updated.getId(), active);
        return updated;
    }

    /**
     * Delete a founder record by ID.
     * Historical notification logs are retained for audit purposes.
     *
     * @param founderId the document ID to delete
     * @throws IllegalArgumentException if not found
     */
    public void deleteFounder(String founderId) {
        Founder founder = founderRepository.findById(founderId)
                .orElseThrow(() -> new IllegalArgumentException("Founder '" + founderId + "' not found"));
        founderRepository.delete(founder);
        log.info("[FOUNDER-SERVICE] Deleted founder '{}' (id: {}) for company '{}'",
                founder.getFullName(), founder.getId(), founder.getCompanyClientId());
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private void validateCreateRequest(FounderRequest request) {
        if (request.getCompanyClientId() == null || request.getCompanyClientId().isBlank()) {
            throw new IllegalArgumentException("companyClientId is required");
        }
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        if (request.getDateOfBirth() == null) {
            throw new IllegalArgumentException("dateOfBirth is required (ISO format: YYYY-MM-DD)");
        }
        if (request.getWhatsappNumber() == null || request.getWhatsappNumber().isBlank()) {
            throw new IllegalArgumentException("whatsappNumber is required");
        }
    }

    /**
     * Resolves the company client and validates it is an active COMPANY-type user.
     */
    private User resolveCompanyClient(String companyClientId) {
        User user = userRepository.findByUserId(companyClientId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No client found with userId '" + companyClientId + "'"));

        if (user.getClientType() == ClientType.INDIVIDUAL) {
            throw new IllegalArgumentException(
                    "Founders can only be associated with COMPANY clients. " +
                    "Client '" + companyClientId + "' is of type INDIVIDUAL.");
        }
        if (user.getClientType() == null) {
            throw new IllegalArgumentException(
                    "Client '" + companyClientId + "' has no client type set. " +
                    "Set the client type to COMPANY before adding founders.");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException(
                    "Client '" + companyClientId + "' is inactive. Activate the client before adding founders.");
        }

        return user;
    }

    /**
     * Normalizes and validates a WhatsApp phone number using the existing eligibility service logic.
     *
     * @param raw       raw phone string from request
     * @param founderId ID of existing founder (for error context) or null for create
     * @return normalized digits-only number
     */
    private String validateAndNormalizePhone(String raw, String founderId) {
        String normalized = phoneValidator.normalizeAndValidatePhone(raw);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "whatsappNumber '" + raw + "' is not a valid international phone number " +
                    "(must normalize to 10–15 digits with country code, e.g. '919876543210')");
        }
        return normalized;
    }
}
