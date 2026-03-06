package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("user_id")
    private String userId;

    @JsonIgnore
    @Field("password")
    private String password;

    @Field("full_name")
    private String fullName;

    @Field("email")
    private String email;

    @Field("phone")
    private String phone;

    @Field("active")
    private boolean active = true;

    @Field("directory_path")
    private String directoryPath;

    @Field("created_by_admin")
    private String createdByAdmin;

    @DBRef
    private List<DocumentAssignment> documentAssignments = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(String userId, String password, String fullName, String email, String createdByAdmin) {
        this.userId = userId;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.createdByAdmin = createdByAdmin;
        this.directoryPath = "uploads/users/" + userId;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public String getCreatedByAdmin() {
        return createdByAdmin;
    }

    public void setCreatedByAdmin(String createdByAdmin) {
        this.createdByAdmin = createdByAdmin;
    }

    public List<DocumentAssignment> getDocumentAssignments() {
        return documentAssignments;
    }

    public void setDocumentAssignments(List<DocumentAssignment> documentAssignments) {
        this.documentAssignments = documentAssignments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", active=" + active +
                '}';
    }
}
