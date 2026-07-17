package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "queries")
public class Query {

    public enum QueryType { TEXT, PDF }
    public enum QueryStatus { OPEN, SEEN, CLOSED }

    @Id
    private String id;

    @Field("subject")
    private String subject;

    @Field("message_text")
    private String messageText;  // populated for TEXT type queries

    @Field("type")
    private QueryType type;      // TEXT or PDF

    @Field("grid_fs_id")
    private String gridFsId;     // GridFS file ID for PDF attachment

    @Field("file_name")
    private String fileName;     // original PDF filename

    @Field("file_size")
    private Long fileSize;

    @Field("raised_by_admin")
    private String raisedByAdmin;

    @DBRef
    @Field("target_user")
    private User targetUser;

    @Field("status")
    private QueryStatus status = QueryStatus.OPEN;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Query() {}

    // ---- Getters and Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public QueryType getType() { return type; }
    public void setType(QueryType type) { this.type = type; }

    public String getGridFsId() { return gridFsId; }
    public void setGridFsId(String gridFsId) { this.gridFsId = gridFsId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getRaisedByAdmin() { return raisedByAdmin; }
    public void setRaisedByAdmin(String raisedByAdmin) { this.raisedByAdmin = raisedByAdmin; }

    public User getTargetUser() { return targetUser; }
    public void setTargetUser(User targetUser) { this.targetUser = targetUser; }

    public QueryStatus getStatus() { return status; }
    public void setStatus(QueryStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Query{" +
                "id='" + id + '\'' +
                ", subject='" + subject + '\'' +
                ", type=" + type +
                ", status=" + status +
                '}';
    }
}
