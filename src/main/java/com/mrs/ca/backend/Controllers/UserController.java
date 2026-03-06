package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private boolean isAuthorized(String pathUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return true;
        return auth.getName().equals(pathUserId);
    }

    // ===================== My Documents =====================

    @GetMapping("/{userId}/documents")
    public ResponseEntity<?> getMyDocuments(@PathVariable String userId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            List<Document> documents = userService.getMyDocuments(userId);
            return ResponseEntity.ok(documents);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/documents/{documentId}")
    public ResponseEntity<?> getDocument(@PathVariable String userId,
                                         @PathVariable String documentId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            Document document = userService.getDocumentById(documentId, userId);
            return ResponseEntity.ok(document);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/documents/{documentId}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable String userId,
                                               @PathVariable String documentId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            Document document = userService.getDocumentById(documentId, userId);

            Path file = Paths.get(document.getFilePath());
            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "File not found on server"));
            }

            String contentType = document.getFileType() != null
                    ? document.getFileType()
                    : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getFileName() + "\"")
                    .body(resource);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (MalformedURLException e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Could not read file"));
        }
    }

    // ===================== Profile =====================

    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            User user = userService.getProfile(userId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
