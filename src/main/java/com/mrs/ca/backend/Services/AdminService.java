package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAssignmentRepository documentAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        DocumentRepository documentRepository,
                        DocumentAssignmentRepository documentAssignmentRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentAssignmentRepository = documentAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ===================== Authentication =====================

    public boolean authenticateAdmin(String username, String password) {
        if (adminUsername == null || adminPassword == null || username == null || password == null) return false;
        boolean userMatch = java.security.MessageDigest.isEqual(
                adminUsername.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                username.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean passMatch = java.security.MessageDigest.isEqual(
                adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return userMatch && passMatch;
    }

    // ===================== User Management =====================

    public User createUser(String userId, String password, String fullName,
                           String email, String phone) {

        if (userRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User ID '" + userId + "' already exists");
        }

        User user = new User(userId, passwordEncoder.encode(password), fullName, email, adminUsername);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Change a user's password.
     */
    public User changePassword(String userId, String newPassword) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    // ===================== Document Management =====================

    /**
     * Upload a document and assign it to a user's collection.
     * The document is owned by the user and an assignment record is created.
     */
    public Document uploadDocument(String title, String description, String fileName,
                                    String filePath, String fileType, Long fileSize,
                                    String category, String userId) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));

        // Create the document owned by this user
        Document document = new Document(title, description, fileName, filePath,
                fileType, fileSize, category, adminUsername, user);
        document = documentRepository.save(document);

        // Create the assignment linking document to user
        DocumentAssignment assignment = new DocumentAssignment(user, document, adminUsername,
                "Uploaded by admin");
        documentAssignmentRepository.save(assignment);

        return document;
    }

    /**
     * Get documents — all docs or filtered by userId.
     */
    public List<Document> getDocuments(String userId) {
        if (userId != null && !userId.isBlank()) {
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));
            return documentRepository.findByOwnerUserId(user.getId());
        }
        return documentRepository.findAll();
    }

    /**
     * Soft-delete a document.
     */
    public Document deleteDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.markDeleted();
        return documentRepository.save(document);
    }
}
