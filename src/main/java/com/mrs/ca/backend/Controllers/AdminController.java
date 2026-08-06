package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import com.mrs.ca.backend.Services.QueryService;
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
    private final QueryService queryService;

    public AdminController(AdminService adminService, QueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
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

    // ===================== Query Management =====================

    /**
     * Raise a TEXT query to a specific user/company.
     * Body: { "userId": "...", "subject": "...", "message": "..." }
     */
    @PostMapping("/queries/text")
    public ResponseEntity<?> raiseTextQuery(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String subject = request.get("subject");
        String message = request.get("message");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "subject is required"));
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required for text queries"));
        }

        try {
            Query query = queryService.raiseTextQuery(userId, subject, message);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Query raised successfully",
                            "queryId", query.getId(),
                            "type", query.getType().name(),
                            "status", query.getStatus().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Raise a PDF query to a specific user/company.
     * Multipart form: userId, subject, file (PDF only).
     */
    @PostMapping("/queries/pdf")
    public ResponseEntity<?> raisePdfQuery(
            @RequestParam("userId") String userId,
            @RequestParam("subject") String subject,
            @RequestParam("file") MultipartFile file) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "subject is required"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }

        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF files are allowed for PDF queries"));
        }

        try {
            Query query = queryService.raisePdfQuery(userId, subject, file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "PDF query raised successfully",
                            "queryId", query.getId(),
                            "type", query.getType().name(),
                            "fileName", query.getFileName(),
                            "status", query.getStatus().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store PDF: " + e.getMessage()));
        }
    }

    /**
     * Raise a mixed query (text + optional attachment) to a specific user/company.
     * Multipart form: userId (required), subject (required),
     *                 message (optional), file (optional — PDF, JPEG, or PNG).
     */
    @PostMapping("/queries/mixed")
    public ResponseEntity<?> raiseMixedQuery(
            @RequestParam("userId") String userId,
            @RequestParam("subject") String subject,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "subject is required"));
        }
        if ((message == null || message.isBlank()) && (file == null || file.isEmpty())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "At least a message or a file attachment is required"));
        }

        // Validate file type if a file was provided
        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
                log.warn("Blocked mixed-query upload with disallowed content-type '{}' for userId={}",
                        contentType, userId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unsupported file type. Allowed: PDF, JPEG, PNG"));
            }
        }

        try {
            Query query = queryService.raiseQueryWithAttachment(userId, subject, message, file);
            var responseBody = new java.util.LinkedHashMap<String, Object>();
            responseBody.put("message", "Query raised successfully");
            responseBody.put("queryId", query.getId());
            responseBody.put("type", query.getType().name());
            responseBody.put("status", query.getStatus().name());
            if (query.getFileName() != null) {
                responseBody.put("fileName", query.getFileName());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store attachment: " + e.getMessage()));
        }
    }

    /**
     * List all queries raised by admin.
     * Optional query param: ?userId= to filter by company/user.
     */
    @GetMapping("/queries")
    public ResponseEntity<?> getAllQueries(
            @RequestParam(value = "userId", required = false) String userId) {
        try {
            List<Query> queries = queryService.getAllQueries(userId);
            return ResponseEntity.ok(queries);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a query (removes PDF from GridFS if present).
     */
    @DeleteMapping("/queries/{queryId}")
    public ResponseEntity<?> deleteQuery(@PathVariable String queryId) {
        try {
            queryService.deleteQuery(queryId);
            return ResponseEntity.ok(Map.of("message", "Query deleted", "queryId", queryId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Admin downloads the PDF attachment of a query.
     */
    @GetMapping("/queries/{queryId}/download")
    public void downloadQueryFile(@PathVariable String queryId,
                                  HttpServletResponse response) throws IOException {
        try {
            queryService.streamQueryFileForAdmin(queryId, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Test SMTP email configuration.
     * Body: { "email": "recipient@example.com" }
     */
    @PostMapping("/queries/test-email")
    public ResponseEntity<?> testEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "recipient email is required"));
        }
        String result = queryService.testEmailConfiguration(email);
        if (result.startsWith("Success")) {
            return ResponseEntity.ok(Map.of("message", result));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result));
        }
    }
}
