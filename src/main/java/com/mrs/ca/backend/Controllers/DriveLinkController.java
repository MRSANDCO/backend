package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.DriveLink;
import com.mrs.ca.backend.Services.DriveLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DriveLinkController {

    private static final Logger log = LoggerFactory.getLogger(DriveLinkController.class);

    private final DriveLinkService driveLinkService;

    public DriveLinkController(DriveLinkService driveLinkService) {
        this.driveLinkService = driveLinkService;
    }

    // ===================== Admin Routes =====================

    // POST /api/admin/drive-links — share a Drive link with a client
    @PostMapping("/api/admin/drive-links")
    public ResponseEntity<?> saveDriveLink(@RequestBody Map<String, String> request) {
        try {
            String userId      = request.get("userId");
            String year        = request.get("year");
            String driveUrl    = request.get("driveUrl");
            String title       = request.get("title");
            String description = request.get("description");

            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId is required"));
            }

            DriveLink link = driveLinkService.saveDriveLink(
                    userId, year, driveUrl, title, description);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Drive link shared successfully",
                            "id", link.getId(),
                            "userId", link.getUserId()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/admin/drive-links?userId=xxx — get all links (optionally filtered)
    @GetMapping("/api/admin/drive-links")
    public ResponseEntity<List<DriveLink>> getAllDriveLinks(
            @RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(driveLinkService.getAllDriveLinks(userId));
    }

    // DELETE /api/admin/drive-links/{id} — delete a Drive link
    @DeleteMapping("/api/admin/drive-links/{id}")
    public ResponseEntity<?> deleteDriveLink(@PathVariable String id) {
        try {
            driveLinkService.deleteDriveLink(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Drive link deleted successfully",
                    "id", id
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // PUT /api/admin/drive-links/{id} — edit/update an existing Drive link
    @PutMapping("/api/admin/drive-links/{id}")
    public ResponseEntity<?> updateDriveLink(@PathVariable String id, @RequestBody Map<String, String> request) {
        try {
            String userId      = request.get("userId");
            String year        = request.get("year");
            String driveUrl    = request.get("driveUrl");
            String title       = request.get("title");
            String description = request.get("description");

            DriveLink updatedLink = driveLinkService.updateDriveLink(
                    id, userId, year, driveUrl, title, description);

            return ResponseEntity.ok(Map.of(
                    "message", "Drive link updated successfully",
                    "id", updatedLink.getId(),
                    "userId", updatedLink.getUserId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===================== Client Routes =====================

    // GET /api/user/{userId}/drive-links — client sees only their links
    @GetMapping("/api/user/{userId}/drive-links")
    public ResponseEntity<List<DriveLink>> getUserDriveLinks(@PathVariable String userId) {
        return ResponseEntity.ok(driveLinkService.getUserDriveLinks(userId));
    }
}