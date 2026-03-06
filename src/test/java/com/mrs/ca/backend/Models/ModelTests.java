package com.mrs.ca.backend.Models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ModelTests {

    // ===================== User =====================

    @Nested
    @DisplayName("User model")
    class UserTests {

        @Test
        @DisplayName("constructor should set all fields and auto-generate directoryPath")
        void constructor() {
            User user = new User("user01", "pass", "John Doe", "j@m.com", "admin");

            assertThat(user.getUserId()).isEqualTo("user01");
            assertThat(user.getPassword()).isEqualTo("pass");
            assertThat(user.getFullName()).isEqualTo("John Doe");
            assertThat(user.getEmail()).isEqualTo("j@m.com");
            assertThat(user.getCreatedByAdmin()).isEqualTo("admin");
            assertThat(user.getDirectoryPath()).isEqualTo("uploads/users/user01");
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("setters should update fields")
        void setters() {
            User user = new User();
            user.setUserId("u1");
            user.setPhone("555");
            user.setActive(false);

            assertThat(user.getUserId()).isEqualTo("u1");
            assertThat(user.getPhone()).isEqualTo("555");
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("toString should contain key fields")
        void toStringTest() {
            User user = new User("user01", "pass", "John", "j@m.com", "admin");
            String str = user.toString();

            assertThat(str).contains("user01", "John", "j@m.com");
        }
    }

    // ===================== Document =====================

    @Nested
    @DisplayName("Document model")
    class DocumentTests {

        @Test
        @DisplayName("constructor should set all fields with ACTIVE status")
        void constructor() {
            User owner = new User("u1", "p", "J", "j@m.com", "admin");
            Document doc = new Document("Tax Return", "2024 filing", "tax.pdf",
                    "/path/tax.pdf", "application/pdf", 2048L, "tax", "admin", owner);

            assertThat(doc.getTitle()).isEqualTo("Tax Return");
            assertThat(doc.getDescription()).isEqualTo("2024 filing");
            assertThat(doc.getFileName()).isEqualTo("tax.pdf");
            assertThat(doc.getFileSize()).isEqualTo(2048L);
            assertThat(doc.getCategory()).isEqualTo("tax");
            assertThat(doc.getUploadedByAdmin()).isEqualTo("admin");
            assertThat(doc.getOwnerUser()).isEqualTo(owner);
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
            assertThat(doc.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("markDeleted should set DELETED status and deletedAt timestamp")
        void markDeleted() {
            Document doc = new Document("T", "D", "f.pdf", "/p", "pdf", 1L, "c", "admin", null);
            assertThat(doc.getDeletedAt()).isNull();

            doc.markDeleted();

            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.DELETED);
            assertThat(doc.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("bumpVersion should increment version and update file fields")
        void bumpVersion() {
            Document doc = new Document("T", "D", "old.pdf", "/old", "pdf", 100L, "c", "admin", null);
            assertThat(doc.getVersion()).isEqualTo(1);

            doc.bumpVersion("new.pdf", "/new", 200L, "application/pdf");

            assertThat(doc.getVersion()).isEqualTo(2);
            assertThat(doc.getFileName()).isEqualTo("new.pdf");
            assertThat(doc.getFilePath()).isEqualTo("/new");
            assertThat(doc.getFileSize()).isEqualTo(200L);
            assertThat(doc.getFileType()).isEqualTo("application/pdf");
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.UPDATED);
        }
    }

    // ===================== DocumentAssignment =====================

    @Nested
    @DisplayName("DocumentAssignment model")
    class DocumentAssignmentTests {

        @Test
        @DisplayName("constructor should set fields with ACTIVE status")
        void constructor() {
            User user = new User("u1", "p", "J", "j@m.com", "admin");
            Document doc = new Document("T", "D", "f.pdf", "/p", "pdf", 1L, "c", "admin", user);

            DocumentAssignment assignment = new DocumentAssignment(user, doc, "admin", "Initial upload");

            assertThat(assignment.getUser()).isEqualTo(user);
            assertThat(assignment.getDocument()).isEqualTo(doc);
            assertThat(assignment.getAssignedByAdmin()).isEqualTo("admin");
            assertThat(assignment.getRemarks()).isEqualTo("Initial upload");
            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        }

        @Test
        @DisplayName("revoke should set REVOKED status and revokedAt timestamp")
        void revoke() {
            DocumentAssignment assignment = new DocumentAssignment();
            assertThat(assignment.getRevokedAt()).isNull();

            assignment.revoke();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.REVOKED);
            assertThat(assignment.getRevokedAt()).isNotNull();
        }
    }
}
