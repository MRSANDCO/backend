package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.DriveLink;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.DriveLinkRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriveLinkServiceTest {

    @Mock
    private DriveLinkRepository driveLinkRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DriveLinkService driveLinkService;

    @BeforeEach
    void injectAdminCredentials() throws Exception {
        // Inject @Value field manually
        Field field = driveLinkService.getClass().getDeclaredField("adminUsername");
        field.setAccessible(true);
        field.set(driveLinkService, "admin_user");
    }

    @Nested
    @DisplayName("saveDriveLink")
    class SaveDriveLink {

        @Test
        @DisplayName("should save link when input is valid and user exists")
        void success() {
            User user = new User("user01", "pass", "John Doe", "john@mail.com", "12345");
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(driveLinkRepository.save(any(DriveLink.class))).thenAnswer(inv -> {
                DriveLink link = inv.getArgument(0);
                link.setId("link123");
                return link;
            });

            DriveLink saved = driveLinkService.saveDriveLink(
                    "user01", "2026", "https://drive.google.com/test", "Tax Folder", "Description");

            assertThat(saved.getId()).isEqualTo("link123");
            assertThat(saved.getUserId()).isEqualTo("user01");
            assertThat(saved.getYear()).isEqualTo("2026");
            assertThat(saved.getDriveUrl()).isEqualTo("https://drive.google.com/test");
            assertThat(saved.getTitle()).isEqualTo("Tax Folder");
            assertThat(saved.getSharedByAdmin()).isEqualTo("admin_user");

            verify(userRepository).findByUserId("user01");
            verify(driveLinkRepository).save(any(DriveLink.class));
        }

        @Test
        @DisplayName("should throw error if user does not exist")
        void userNotFound() {
            when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> driveLinkService.saveDriveLink(
                    "missing", "2026", "https://drive.google.com/test", "Tax Folder", "Description"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User 'missing' not found");

            verify(driveLinkRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw error if drive URL is blank")
        void blankDriveUrl() {
            User user = new User("user01", "pass", "John", "j@m.com", null);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> driveLinkService.saveDriveLink(
                    "user01", "2026", "", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Drive URL is required");
        }

        @Test
        @DisplayName("should throw error if year is blank")
        void blankYear() {
            User user = new User("user01", "pass", "John", "j@m.com", null);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> driveLinkService.saveDriveLink(
                    "user01", " ", "https://url.com", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Year is required");
        }
    }

    @Nested
    @DisplayName("updateDriveLink")
    class UpdateDriveLink {

        @Test
        @DisplayName("should update link when fields are valid")
        void success() {
            DriveLink existing = new DriveLink("user01", "2025", "http://old", "Old Title", "Old Desc", "admin");
            existing.setId("link123");

            User user = new User("user02", "pass", "Jane Doe", "jane@mail.com", "54321");

            when(driveLinkRepository.findById("link123")).thenReturn(Optional.of(existing));
            when(userRepository.findByUserId("user02")).thenReturn(Optional.of(user));
            when(driveLinkRepository.save(any(DriveLink.class))).thenAnswer(inv -> inv.getArgument(0));

            DriveLink updated = driveLinkService.updateDriveLink(
                    "link123", "user02", "2026", "https://new-url", "New Title", "New Desc");

            assertThat(updated.getId()).isEqualTo("link123");
            assertThat(updated.getUserId()).isEqualTo("user02");
            assertThat(updated.getYear()).isEqualTo("2026");
            assertThat(updated.getDriveUrl()).isEqualTo("https://new-url");
            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getDescription()).isEqualTo("New Desc");

            verify(driveLinkRepository).findById("link123");
            verify(userRepository).findByUserId("user02");
            verify(driveLinkRepository).save(existing);
        }

        @Test
        @DisplayName("should throw error if link does not exist")
        void linkNotFound() {
            when(driveLinkRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> driveLinkService.updateDriveLink(
                    "missing", "user01", "2026", "http://url", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Drive link not found");

            verify(driveLinkRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw error if updated user ID does not exist")
        void userNotFound() {
            DriveLink existing = new DriveLink("user01", "2025", "http://old", "Title", "Desc", "admin");
            existing.setId("link123");

            when(driveLinkRepository.findById("link123")).thenReturn(Optional.of(existing));
            when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> driveLinkService.updateDriveLink(
                    "link123", "missing", "2026", "http://url", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User 'missing' not found");

            verify(driveLinkRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw error if drive URL is blank during update")
        void blankDriveUrl() {
            DriveLink existing = new DriveLink("user01", "2025", "http://old", "Title", "Desc", "admin");
            existing.setId("link123");

            User user = new User("user01", "pass", "John", "j@m.com", null);
            when(driveLinkRepository.findById("link123")).thenReturn(Optional.of(existing));
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> driveLinkService.updateDriveLink(
                    "link123", "user01", "2026", "", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Drive URL is required");
        }

        @Test
        @DisplayName("should throw error if year is blank during update")
        void blankYear() {
            DriveLink existing = new DriveLink("user01", "2025", "http://old", "Title", "Desc", "admin");
            existing.setId("link123");

            User user = new User("user01", "pass", "John", "j@m.com", null);
            when(driveLinkRepository.findById("link123")).thenReturn(Optional.of(existing));
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> driveLinkService.updateDriveLink(
                    "link123", "user01", " ", "https://url.com", "Title", "Desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Year is required");
        }
    }

    @Nested
    @DisplayName("deleteDriveLink")
    class DeleteDriveLink {

        @Test
        @DisplayName("should delete link when it exists")
        void success() {
            DriveLink existing = new DriveLink("user01", "2025", "http://old", "Title", "Desc", "admin");
            existing.setId("link123");

            when(driveLinkRepository.findById("link123")).thenReturn(Optional.of(existing));

            driveLinkService.deleteDriveLink("link123");

            verify(driveLinkRepository).delete(existing);
        }

        @Test
        @DisplayName("should throw error if deleting non-existent link")
        void notFound() {
            when(driveLinkRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> driveLinkService.deleteDriveLink("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Drive link not found");

            verify(driveLinkRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("getAllDriveLinks should order by createdAt desc")
    void getAllDriveLinks() {
        driveLinkService.getAllDriveLinks(null);
        verify(driveLinkRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("getAllDriveLinks with userId should filter by userId")
    void getAllDriveLinksWithUser() {
        driveLinkService.getAllDriveLinks("user01");
        verify(driveLinkRepository).findByUserId("user01");
    }

    @Test
    @DisplayName("getUserDriveLinks should query repository by userId")
    void getUserDriveLinks() {
        driveLinkService.getUserDriveLinks("user01");
        verify(driveLinkRepository).findByUserId("user01");
    }

    @Test
    @DisplayName("deleteAllLinksForUser should delete all links for specific userId")
    void deleteAllLinksForUser() {
        driveLinkService.deleteAllLinksForUser("user01");
        verify(driveLinkRepository).deleteByUserId("user01");
    }
}
