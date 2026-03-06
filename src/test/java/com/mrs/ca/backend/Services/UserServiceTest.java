package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GridFsTemplate gridFsTemplate;
    @Mock private GridFsOperations gridFsOperations;

    @InjectMocks private UserService userService;

    private User createTestUser(String userId, boolean active) {
        User user = new User(userId, "password", "Test User", userId + "@mail.com", "admin");
        user.setId("id-" + userId);
        user.setActive(active);
        return user;
    }

    // ===================== Authentication =====================

    @Nested
    @DisplayName("authenticateUser")
    class AuthenticateUser {

        @Test
        @DisplayName("should return user for valid credentials")
        void success() {
            User user = createTestUser("user01", true);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "password")).thenReturn(true);

            Optional<User> result = userService.authenticateUser("user01", "password");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo("user01");
        }

        @Test
        @DisplayName("should return empty for wrong password")
        void wrongPassword() {
            User user = createTestUser("user01", true);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            Optional<User> result = userService.authenticateUser("user01", "wrongpass");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for inactive user")
        void inactiveUser() {
            User user = createTestUser("user01", false);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            Optional<User> result = userService.authenticateUser("user01", "password");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent user")
        void notFound() {
            when(userRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            Optional<User> result = userService.authenticateUser("ghost", "password");

            assertThat(result).isEmpty();
        }
    }

    // ===================== Documents =====================

    @Nested
    @DisplayName("getMyDocuments")
    class GetMyDocuments {

        @Test
        @DisplayName("should return active documents for user")
        void success() {
            User user = createTestUser("user01", true);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(documentRepository.findByOwnerUserIdAndStatus(user.getId(), DocumentStatus.ACTIVE))
                    .thenReturn(List.of());

            List<Document> docs = userService.getMyDocuments("user01");

            assertThat(docs).isEmpty();
            verify(documentRepository).findByOwnerUserIdAndStatus(user.getId(), DocumentStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw if user not found")
        void userNotFound() {
            when(userRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyDocuments("ghost"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getDocumentById")
    class GetDocumentById {

        @Test
        @DisplayName("should return document if user owns it")
        void success() {
            User user = createTestUser("user01", true);
            Document doc = new Document("T", "D", "f.pdf", "/p", "pdf", 1L, "cat", "admin", user);
            doc.setId("doc1");

            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(documentRepository.findById("doc1")).thenReturn(Optional.of(doc));

            Document result = userService.getDocumentById("doc1", "user01");

            assertThat(result.getId()).isEqualTo("doc1");
        }

        @Test
        @DisplayName("should throw SecurityException if user does not own document")
        void accessDenied() {
            User owner = createTestUser("owner", true);
            User requester = createTestUser("requester", true);
            Document doc = new Document("T", "D", "f.pdf", "/p", "pdf", 1L, "cat", "admin", owner);
            doc.setId("doc1");

            when(userRepository.findByUserId("requester")).thenReturn(Optional.of(requester));
            when(documentRepository.findById("doc1")).thenReturn(Optional.of(doc));

            assertThatThrownBy(() -> userService.getDocumentById("doc1", "requester"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("should throw if document not found")
        void documentNotFound() {
            User user = createTestUser("user01", true);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));
            when(documentRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getDocumentById("missing", "user01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ===================== Profile =====================

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("should return user profile")
        void success() {
            User user = createTestUser("user01", true);
            when(userRepository.findByUserId("user01")).thenReturn(Optional.of(user));

            User result = userService.getProfile("user01");

            assertThat(result.getUserId()).isEqualTo("user01");
        }

        @Test
        @DisplayName("should throw if user not found")
        void notFound() {
            when(userRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile("ghost"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
