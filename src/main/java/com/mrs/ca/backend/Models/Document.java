package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
public class Document {

    @Id
    private String id;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("file_name")
    private String fileName;

    @Field("file_path")
    private String filePath;

    @Field("file_type")
    private String fileType;

    @Field("file_size")
    private Long fileSize;

    @Field("category")
    private String category;

    @Field("status")
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Field("version")
    private int version = 1;

    @Field("original_document_id")
    private String originalDocumentId;

    @Field("uploaded_by_admin")
    private String uploadedByAdmin;

    @DBRef
    @Field("owner_user")
    private User ownerUser;

    @DBRef
    private List<DocumentAssignment> documentAssignments = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("deleted_at")
    private LocalDateTime deletedAt;

    public Document() {
    }

    public Document(String title, String description, String fileName, String filePath,
                    String fileType, Long fileSize, String category,
                    String uploadedByAdmin, User ownerUser) {
        this.title = title;
        this.description = description;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.category = category;
        this.uploadedByAdmin = uploadedByAdmin;
        this.ownerUser = ownerUser;
    }

    public void markDeleted() {
        this.status = DocumentStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    /** Bump version when admin replaces the file. */
    public void bumpVersion(String newFileName, String newFilePath, Long newFileSize, String newFileType) {
        this.version++;
        this.fileName = newFileName;
        this.filePath = newFilePath;
        this.fileSize = newFileSize;
        this.fileType = newFileType;
        this.status = DocumentStatus.UPDATED;
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getOriginalDocumentId() {
        return originalDocumentId;
    }

    public void setOriginalDocumentId(String originalDocumentId) {
        this.originalDocumentId = originalDocumentId;
    }

    public String getUploadedByAdmin() {
        return uploadedByAdmin;
    }

    public void setUploadedByAdmin(String uploadedByAdmin) {
        this.uploadedByAdmin = uploadedByAdmin;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(User ownerUser) {
        this.ownerUser = ownerUser;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", fileName='" + fileName + '\'' +
                ", status=" + status +
                ", version=" + version +
                '}';
    }
}
