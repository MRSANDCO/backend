package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.DriveLink;
import com.mrs.ca.backend.Repositories.DriveLinkRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriveLinkService {

    private static final Logger log = LoggerFactory.getLogger(DriveLinkService.class);

    @Value("${app.admin.username}")
    private String adminUsername;

    private final DriveLinkRepository driveLinkRepository;
    private final UserRepository userRepository;

    public DriveLinkService(DriveLinkRepository driveLinkRepository,
                            UserRepository userRepository) {
        this.driveLinkRepository = driveLinkRepository;
        this.userRepository = userRepository;
    }

    // Admin: save a new Drive link for a user
    public DriveLink saveDriveLink(String userId, String year, String driveUrl,
                                   String title, String description) {

        // Validate user exists
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));

        if (driveUrl == null || driveUrl.isBlank()) {
            throw new IllegalArgumentException("Drive URL is required");
        }
        if (year == null || year.isBlank()) {
            throw new IllegalArgumentException("Year is required");
        }

        DriveLink link = new DriveLink(userId, year, driveUrl, title, description, adminUsername);
        DriveLink saved = driveLinkRepository.save(link);

        log.info("[DRIVE] Link '{}' shared with userId='{}' for year='{}'",
                 title, userId, year);
        return saved;
    }

    // Admin: get all Drive links (optionally filtered by userId)
    public List<DriveLink> getAllDriveLinks(String userId) {
        if (userId != null && !userId.isBlank()) {
            return driveLinkRepository.findByUserId(userId);
        }
        return driveLinkRepository.findAllByOrderByCreatedAtDesc();
    }

    // Client: get only their own Drive links
    public List<DriveLink> getUserDriveLinks(String userId) {
        return driveLinkRepository.findByUserId(userId);
    }

    // Admin: delete a Drive link by id
    public void deleteDriveLink(String id) {
        DriveLink link = driveLinkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Drive link not found"));
        driveLinkRepository.delete(link);
        log.info("[DRIVE] Link id='{}' deleted.", id);
    }

    // Called when a user is deleted — clean up their links too
    public void deleteAllLinksForUser(String userId) {
        driveLinkRepository.deleteByUserId(userId);
        log.info("[DRIVE] All links deleted for userId='{}'", userId);
    }
}