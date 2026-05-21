package com.mrs.ca.backend.Models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@org.springframework.data.mongodb.core.mapping.Document(collection = "drive_links")
public class DriveLink {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("year")
    private String year;

    @Field("drive_url")
    private String driveUrl;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("shared_by_admin")
    private String sharedByAdmin;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    public DriveLink() {}

    public DriveLink(String userId, String year, String driveUrl,
                     String title, String description, String sharedByAdmin) {
        this.userId = userId;
        this.year = year;
        this.driveUrl = driveUrl;
        this.title = title;
        this.description = description;
        this.sharedByAdmin = sharedByAdmin;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getDriveUrl() { return driveUrl; }
    public void setDriveUrl(String driveUrl) { this.driveUrl = driveUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSharedByAdmin() { return sharedByAdmin; }
    public void setSharedByAdmin(String sharedByAdmin) { this.sharedByAdmin = sharedByAdmin; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "DriveLink{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", year='" + year + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}