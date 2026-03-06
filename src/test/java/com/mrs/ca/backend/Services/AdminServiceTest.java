package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentAssignmentRepository documentAssignmentRepository;

    @InjectMocks private AdminService adminService;

    @BeforeEach
    void injectAdminCredentials() throws Exception {
        // Inject @Value fields manually since we are not using Spring context
        setField(adminService, "adminUsername", "admin");
        setField(adminService, "adminPassword", "admin123");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ===================== Authentication =====================

    @Nested
    @DisplayName("authenticateAdmin")
    class AuthenticateAdmin {

        @Test
        @DisplayName("should return true for valid credentials")
        void success() {
            assertThat(adminService.authenticateAdmin("admin", "admin123")).isTrue();
        }

        @Test
        @DisplayName("should return false for wrong username")
        void wrongUsername() {
            assertThat(adminService.authenticateAdmin("wrong", "admin123")).isFalse();
        }

        @Test
        @DisplayName("should return false for wrong password")
        void wrongPassword() {
            assertThat(adminService.authenticateAdmin("admin", "wrong")).isFalse();
        }
    }

    // ===================== User Management =====================

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create and save a new user")
        void success() {
            when(userRepository.existsByUserId("user01")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("generatedId");
                return u;
            });

            User user = adminService.createUser("user01", "pass", "John", "john@mail.com", "12345");

            assertThat(user.getUserId()).isEqualTo("user01");
            assertThat(user.getFullName()).isEqualTo("John");
            assertThat(user.getPhone()).isEqualTo("12345");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw when userId already exists")
        void duplicateUserId() {
            when(userRepository.existsByUserId("user01")).thenReturn(true);

            assertThatThrownBy(() ->
                    adminService.createUser("user01", "pass", "John", "john@mail.com", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(userRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("getAllUsers should delegate to repository")
    void getAllUsers() {
        User u1 = new User("u1", "p", "A", "a@m.com", "admin");
        User u2 = new User("u2", "p", "B", "b@m.com", "admin");
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<User> users = adminService.getAllUsers();

        assertThat(users).hasSize(2);
        verify(userRepository).findAll();
    }

    // ===================== Document Management =====================

    @Nested
    @DisplayName("uploadDocument")
    class UploadDocument {

        @Test
        @DisplayName("should save document and create assignment")
        void success() {
            User user = new User("user01", "pass", "John", "j@m.com", "admin");
            user.setId("uid1");
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                d.setId("docId1");
                return d;
            });
            when(documentAssignmentRepository.save(any(DocumentAssignment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Document doc = adminService.uploadDocument(
                    "Title", "Desc", "file.pdf", "/path/file.pdf", "application/pdf",
                    1024L, "tax", "user01");

            assertThat(doc.getTitle()).isEqualTo("Title");
            assertThat(doc.getOwnerUser()).isEqualTo(user);
            verify(documentRepository).save(any(Document.class));
            verify(documentAssignmentRepository).save(any(DocumentAssignment.class));
        }

        @Test
        @DisplayName("should throw if user not found")
        void userNotFound() {
            when(userRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    adminService.uploadDocument("T", "D", "f.pdf", "/p", "pdf", 1L, "cat", "ghost"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getDocuments")
    class GetDocuments {

        @Test
        @DisplayName("should return all documents when userId is null")
        void allDocuments() {
            when(documentRepository.findAll()).thenReturn(List.of());

            adminService.getDocuments(null);

            verify(documentRepository).findAll();
            verify(documentRepository, never()).findByOwnerUserId(any());
        }

        @Test
        @DisplayName("should return user documents when userId is provided")
        void byUserId() {
            User user = new User("user01", "p", "J", "j@m.com", "admin");
            user.setId("uid1");
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(documentRepository.findByOwnerUserId("uid1")).thenReturn(List.of());

            adminService.getDocuments("user01");

            verify(documentRepository).findByOwnerUserId("uid1");
        }
    }

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocument {

        @Test
        @DisplayName("should mark document as deleted")
        void success() {
            Document doc = new Document("T", "D", "f.pdf", "/p", "pdf", 1L, "cat", "admin", null);
            doc.setId("docId1");
            when(documentRepository.findById("docId1")).thenReturn(Optional.of(doc));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = adminService.deleteDocument("docId1");

            assertThat(result.getStatus()).isEqualTo(DocumentStatus.DELETED);
            verify(documentRepository).save(doc);
        }

        @Test
        @DisplayName("should throw if document not found")
        void notFound() {
            when(documentRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteDocument("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
