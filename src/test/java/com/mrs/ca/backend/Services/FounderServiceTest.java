package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.Founder;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.FounderRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.FounderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FounderService}.
 */
@ExtendWith(MockitoExtension.class)
class FounderServiceTest {

    @Mock private FounderRepository founderRepository;
    @Mock private UserRepository userRepository;
    @Mock private AnniversaryEligibilityService phoneValidator;

    private FounderService founderService;

    @BeforeEach
    void setUp() {
        founderService = new FounderService(founderRepository, userRepository, phoneValidator);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private User companyUser(String userId) {
        User u = new User();
        u.setUserId(userId);
        u.setFullName("Test Company");
        u.setClientType(ClientType.COMPANY);
        u.setActive(true);
        return u;
    }

    private FounderRequest validRequest(String companyClientId) {
        FounderRequest r = new FounderRequest();
        r.setCompanyClientId(companyClientId);
        r.setFullName("Ravi Shankar");
        r.setDateOfBirth(LocalDate.of(1980, 6, 15));
        r.setWhatsappNumber("+919876543210");
        r.setBirthdayGreetingEnabled(true);
        r.setWhatsappConsentGiven(true);
        return r;
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    @Test
    void createFounder_Success_ReturnsSavedFounder() {
        when(userRepository.findByUserId("MRS001")).thenReturn(Optional.of(companyUser("MRS001")));
        when(phoneValidator.normalizeAndValidatePhone("+919876543210")).thenReturn("919876543210");
        when(founderRepository.existsByCompanyClientIdAndWhatsappNumber("MRS001", "919876543210")).thenReturn(false);

        Founder saved = new Founder();
        saved.setId("f001");
        saved.setCompanyClientId("MRS001");
        saved.setFullName("Ravi Shankar");
        when(founderRepository.save(any(Founder.class))).thenReturn(saved);

        Founder result = founderService.createFounder(validRequest("MRS001"));

        assertThat(result.getId()).isEqualTo("f001");
        assertThat(result.getCompanyClientId()).isEqualTo("MRS001");
        verify(founderRepository).save(any(Founder.class));
    }

    @Test
    void createFounder_IndividualClient_ThrowsIllegalArgument() {
        User individualUser = companyUser("IND001");
        individualUser.setClientType(ClientType.INDIVIDUAL);
        when(userRepository.findByUserId("IND001")).thenReturn(Optional.of(individualUser));

        FounderRequest req = validRequest("IND001");
        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INDIVIDUAL");
    }

    @Test
    void createFounder_NullClientType_ThrowsIllegalArgument() {
        User nullTypeUser = new User();
        nullTypeUser.setUserId("X001");
        nullTypeUser.setActive(true);
        nullTypeUser.setClientType(null);
        when(userRepository.findByUserId("X001")).thenReturn(Optional.of(nullTypeUser));

        FounderRequest req = validRequest("X001");
        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no client type set");
    }

    @Test
    void createFounder_NonExistentClient_ThrowsIllegalArgument() {
        when(userRepository.findByUserId("GHOST")).thenReturn(Optional.empty());

        FounderRequest req = validRequest("GHOST");
        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GHOST");
    }

    @Test
    void createFounder_InvalidPhone_ThrowsIllegalArgument() {
        when(userRepository.findByUserId("MRS001")).thenReturn(Optional.of(companyUser("MRS001")));
        when(phoneValidator.normalizeAndValidatePhone("abc")).thenReturn(null);

        FounderRequest req = validRequest("MRS001");
        req.setWhatsappNumber("abc");

        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid international phone");
    }

    @Test
    void createFounder_DuplicatePhone_ThrowsIllegalArgument() {
        when(userRepository.findByUserId("MRS001")).thenReturn(Optional.of(companyUser("MRS001")));
        when(phoneValidator.normalizeAndValidatePhone("+919876543210")).thenReturn("919876543210");
        when(founderRepository.existsByCompanyClientIdAndWhatsappNumber("MRS001", "919876543210")).thenReturn(true);

        FounderRequest req = validRequest("MRS001");
        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void createFounder_MissingFullName_ThrowsIllegalArgument() {
        FounderRequest req = validRequest("MRS001");
        req.setFullName("");

        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullName is required");
    }

    @Test
    void createFounder_MissingDateOfBirth_ThrowsIllegalArgument() {
        FounderRequest req = validRequest("MRS001");
        req.setDateOfBirth(null);

        assertThatThrownBy(() -> founderService.createFounder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateOfBirth is required");
    }

    // ─── Multiple founders same company ──────────────────────────────────────────

    @Test
    void createFounder_MultipleFoundersSameCompany_AllSucceed() {
        when(userRepository.findByUserId("MRS001")).thenReturn(Optional.of(companyUser("MRS001")));

        // First founder
        when(phoneValidator.normalizeAndValidatePhone("+911111111111")).thenReturn("911111111111");
        when(founderRepository.existsByCompanyClientIdAndWhatsappNumber("MRS001", "911111111111")).thenReturn(false);

        FounderRequest req1 = new FounderRequest();
        req1.setCompanyClientId("MRS001");
        req1.setFullName("Founder One");
        req1.setDateOfBirth(LocalDate.of(1975, 3, 10));
        req1.setWhatsappNumber("+911111111111");

        Founder f1 = new Founder(); f1.setId("f001"); f1.setFullName("Founder One");
        when(founderRepository.save(any())).thenReturn(f1);

        Founder result1 = founderService.createFounder(req1);
        assertThat(result1.getFullName()).isEqualTo("Founder One");

        // Second founder — different phone, same company
        when(phoneValidator.normalizeAndValidatePhone("+912222222222")).thenReturn("912222222222");
        when(founderRepository.existsByCompanyClientIdAndWhatsappNumber("MRS001", "912222222222")).thenReturn(false);

        FounderRequest req2 = new FounderRequest();
        req2.setCompanyClientId("MRS001");
        req2.setFullName("Founder Two");
        req2.setDateOfBirth(LocalDate.of(1985, 8, 20));
        req2.setWhatsappNumber("+912222222222");

        Founder f2 = new Founder(); f2.setId("f002"); f2.setFullName("Founder Two");
        when(founderRepository.save(any())).thenReturn(f2);

        Founder result2 = founderService.createFounder(req2);
        assertThat(result2.getFullName()).isEqualTo("Founder Two");

        verify(founderRepository, times(2)).save(any(Founder.class));
    }

    // ─── Update ──────────────────────────────────────────────────────────────────

    @Test
    void updateFounder_UpdateName_Success() {
        Founder existing = new Founder();
        existing.setId("f001");
        existing.setFullName("Old Name");
        existing.setWhatsappNumber("919876543210");
        existing.setCompanyClientId("MRS001");

        when(founderRepository.findById("f001")).thenReturn(Optional.of(existing));
        when(founderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FounderRequest req = new FounderRequest();
        req.setFullName("New Name");

        Founder updated = founderService.updateFounder("f001", req);
        assertThat(updated.getFullName()).isEqualTo("New Name");
    }

    @Test
    void updateFounder_NotFound_ThrowsIllegalArgument() {
        when(founderRepository.findById("ghost")).thenReturn(Optional.empty());

        FounderRequest req = new FounderRequest();
        req.setFullName("x");

        assertThatThrownBy(() -> founderService.updateFounder("ghost", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ─── Birthday matching (date stored correctly) ────────────────────────────────

    @Test
    void createFounder_DateOfBirth_SetsMonthAndDayCorrectly() {
        when(userRepository.findByUserId("MRS001")).thenReturn(Optional.of(companyUser("MRS001")));
        when(phoneValidator.normalizeAndValidatePhone("+919876543210")).thenReturn("919876543210");
        when(founderRepository.existsByCompanyClientIdAndWhatsappNumber(any(), any())).thenReturn(false);

        Founder[] capturedFounder = new Founder[1];
        when(founderRepository.save(any(Founder.class))).thenAnswer(inv -> {
            capturedFounder[0] = inv.getArgument(0);
            capturedFounder[0].setId("f001");
            return capturedFounder[0];
        });

        FounderRequest req = validRequest("MRS001");
        req.setDateOfBirth(LocalDate.of(1980, 6, 15));

        founderService.createFounder(req);

        // Verify pre-computed fields are set correctly for indexed birthday matching
        assertThat(capturedFounder[0].getBirthdayMonth()).isEqualTo(6);
        assertThat(capturedFounder[0].getBirthdayDay()).isEqualTo(15);
        assertThat(capturedFounder[0].getDateOfBirth()).isEqualTo(LocalDate.of(1980, 6, 15));
    }

    // ─── Activate / deactivate ────────────────────────────────────────────────────

    @Test
    void setActive_Deactivate_AlsoDisablesGreeting() {
        Founder f = new Founder();
        f.setId("f001");
        f.setActive(true);
        f.setBirthdayGreetingEnabled(true);

        when(founderRepository.findById("f001")).thenReturn(Optional.of(f));
        when(founderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Founder updated = founderService.setActive("f001", false);

        assertThat(updated.isActive()).isFalse();
        assertThat(updated.isBirthdayGreetingEnabled()).isFalse();
    }

    // ─── Read operations ──────────────────────────────────────────────────────────

    @Test
    void getFoundersByCompany_ReturnsList() {
        Founder f1 = new Founder(); f1.setId("f001");
        Founder f2 = new Founder(); f2.setId("f002");
        when(founderRepository.findByCompanyClientId("MRS001")).thenReturn(List.of(f1, f2));

        List<Founder> founders = founderService.getFoundersByCompany("MRS001");
        assertThat(founders).hasSize(2);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    @Test
    void deleteFounder_Success_CallsDelete() {
        Founder f = new Founder(); f.setId("f001"); f.setFullName("R");
        when(founderRepository.findById("f001")).thenReturn(Optional.of(f));

        founderService.deleteFounder("f001");

        verify(founderRepository).delete(f);
    }

    @Test
    void deleteFounder_NotFound_ThrowsIllegalArgument() {
        when(founderRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> founderService.deleteFounder("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
