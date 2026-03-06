package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ===================== User Management =====================

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String password = request.get("password");
            String fullName = request.get("fullName");
            String email = request.get("email");
            String phone = request.get("phone");

            if (userId == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId and password are required"));
            }

            User user = adminService.createUser(userId, password, fullName, email, phone);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "User created successfully",
                                 "userId", user.getUserId(),
                                 "id", user.getId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{userId}/password")
    public ResponseEntity<?> changePassword(@PathVariable String userId,
                                             @RequestBody Map<String, String> request) {
        try {
            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "newPassword is required"));
            }
            adminService.changePassword(userId, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully",
                                            "userId", userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===================== Document Management =====================

    @PostMapping("/users/{userId}/documents")
    public ResponseEntity<?> uploadDocument(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
            }

            // Save the file to disk under the user's directory (absolute path)
            Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", "users", userId);
            Files.createDirectories(uploadPath);

            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.ext");
            if (originalFileName.contains("..")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid path sequence in file name"));
            }
            
            String storedFileName = UUID.randomUUID() + "_" + originalFileName;
            Path filePath = uploadPath.resolve(storedFileName);
            file.transferTo(filePath.toFile());

            // Save metadata to MongoDB
            Document document = adminService.uploadDocument(
                    title, description, originalFileName, filePath.toString(),
                    file.getContentType(), file.getSize(),
                    category, userId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Document uploaded successfully",
                                 "documentId", document.getId(),
                                 "fileName", originalFileName));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save file: " + e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(
            @RequestParam(value = "userId", required = false) String userId) {
        try {
            List<Document> documents = adminService.getDocuments(userId);
            return ResponseEntity.ok(documents);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        try {
            Document document = adminService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of("message", "Document deleted",
                                            "documentId", document.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
