package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "document_assignments")
@CompoundIndex(name = "user_document_idx", def = "{'user': 1, 'document': 1}", unique = true)
public class DocumentAssignment {

    @Id
    private String id;

    @DBRef
    @Field("user")
    private User user;

    @DBRef
    @Field("document")
    private com.mrs.ca.backend.Models.Document document;

    @Field("assigned_by_admin")
    private String assignedByAdmin;

    @Field("status")
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @CreatedDate
    @Field("assigned_at")
    private LocalDateTime assignedAt;

    @Field("revoked_at")
    private LocalDateTime revokedAt;

    @Field("remarks")
    private String remarks;

    public DocumentAssignment() {
    }

    public DocumentAssignment(User user, com.mrs.ca.backend.Models.Document document,
                              String assignedByAdmin, String remarks) {
        this.user = user;
        this.document = document;
        this.assignedByAdmin = assignedByAdmin;
        this.remarks = remarks;
    }

    /** Revoke this assignment so the user can no longer see the document. */
    public void revoke() {
        this.status = AssignmentStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public com.mrs.ca.backend.Models.Document getDocument() {
        return document;
    }

    public void setDocument(com.mrs.ca.backend.Models.Document document) {
        this.document = document;
    }

    public String getAssignedByAdmin() {
        return assignedByAdmin;
    }

    public void setAssignedByAdmin(String assignedByAdmin) {
        this.assignedByAdmin = assignedByAdmin;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        return "DocumentAssignment{" +
                "id='" + id + '\'' +
                ", user=" + (user != null ? user.getUserId() : "null") +
                ", document=" + (document != null ? document.getTitle() : "null") +
                ", status=" + status +
                ", assignedAt=" + assignedAt +
                '}';
    }
}
