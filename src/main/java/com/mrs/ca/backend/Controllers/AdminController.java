package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    /** Allowed MIME types for document upload. Extend as needed. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

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

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable String userId,
                                        @RequestBody Map<String, String> request) {
        try {
            User user = adminService.updateUser(userId, 
                                                request.get("fullName"), 
                                                request.get("email"), 
                                                request.get("phone"));
            return ResponseEntity.ok(Map.of("message", "User updated successfully",
                                            "userId", user.getUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        try {
            adminService.deleteUser(userId);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully",
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

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            log.warn("Blocked upload with disallowed content-type '{}' for userId={}", contentType, userId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unsupported file type. Allowed: PDF, JPEG, PNG"));
        }

        try {
            Document document = adminService.uploadDocument(file, title, description, category, userId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Document uploaded successfully",
                                 "documentId", document.getId(),
                                 "fileName", document.getFileName()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store file: " + e.getMessage()));
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
            adminService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of("message", "Document deleted",
                                            "documentId", documentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents/{documentId}/download")
    public void downloadDocument(@PathVariable String documentId,
                                 HttpServletResponse response) throws IOException {
        try {
            adminService.streamDocumentForAdmin(documentId, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
}
