package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "query_responses")
public class QueryResponse {

    public enum SenderRole { CLIENT, ADMIN }

    @Id
    private String id;

    @Indexed
    @Field("query_id")
    private String queryId;

    @Field("client_id")
    private String clientId;

    @Field("sender_id")
    private String senderId;

    @Field("sender_name")
    private String senderName;

    @Field("sender_email")
    private String senderEmail;

    @Field("sender_role")
    private SenderRole senderRole;

    @Field("message")
    private String message;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public QueryResponse() {}

    public QueryResponse(String queryId, String clientId, String senderId, String senderName,
                         String senderEmail, SenderRole senderRole, String message) {
        this.queryId = queryId;
        this.clientId = clientId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.senderRole = senderRole;
        this.message = message;
    }

    // ---- Getters and Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQueryId() { return queryId; }
    public void setQueryId(String queryId) { this.queryId = queryId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public SenderRole getSenderRole() { return senderRole; }
    public void setSenderRole(SenderRole senderRole) { this.senderRole = senderRole; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "QueryResponse{" +
                "id='" + id + '\'' +
                ", queryId='" + queryId + '\'' +
                ", senderRole=" + senderRole +
                ", senderName='" + senderName + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
