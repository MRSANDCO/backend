package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAssignmentRepository documentAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final GridFsTemplate gridFsTemplate;

    public AdminService(UserRepository userRepository,
                        DocumentRepository documentRepository,
                        DocumentAssignmentRepository documentAssignmentRepository,
                        PasswordEncoder passwordEncoder,
                        GridFsTemplate gridFsTemplate) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentAssignmentRepository = documentAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.gridFsTemplate = gridFsTemplate;
    }

    // ===================== Authentication =====================

    public boolean authenticateAdmin(String username, String password) {
        if (adminUsername == null || adminPassword == null || username == null || password == null) return false;
        boolean userMatch = java.security.MessageDigest.isEqual(
                adminUsername.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                username.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!userMatch) {
            log.warn("[AUTH] Failed admin login attempt for username='{}'", username);
            return false;
        }
        if (adminPassword.startsWith("$2a$") || adminPassword.startsWith("$2b$")) {
            boolean ok = passwordEncoder.matches(password, adminPassword);
            if (!ok) log.warn("[AUTH] Wrong password for admin username='{}'", username);
            return ok;
        }
        boolean passMatch = java.security.MessageDigest.isEqual(
                adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!passMatch) log.warn("[AUTH] Wrong password for admin username='{}'", username);
        return passMatch;
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

    /**
     * Return a paginated list of users.
     * Default: page 0, size 50, sorted by created_at descending.
     */
    public List<User> getAllUsers(int page, int size) {
        return userRepository
                .findAll(PageRequest.of(page, Math.min(size, 100),
                         Sort.by(Sort.Direction.DESC, "created_at")))
                .getContent();
    }

    /** Overloaded convenience method with default pagination. */
    public List<User> getAllUsers() {
        return getAllUsers(0, 50);
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
     * The file binary is stored in MongoDB GridFS; only metadata is kept in the Document record.
     */
    public Document uploadDocument(MultipartFile file, String title, String description,
                                   String category, String userId) throws IOException {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

        // Store the binary in GridFS
        ObjectId gridFsObjectId = gridFsTemplate.store(
                file.getInputStream(),
                originalFileName,
                file.getContentType()
        );

        // Create the document record (no local file path)
        Document document = new Document(title, description, originalFileName, null,
                file.getContentType(), file.getSize(), category, adminUsername, user);
        document.setGridFsId(gridFsObjectId.toHexString());
        document = documentRepository.save(document);

        log.info("[UPLOAD] Document '{}' ({}) stored in GridFS for userId='{}'",
                 originalFileName, gridFsObjectId.toHexString(), userId);

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
     * Soft-delete a document and remove its GridFS binary.
     */
    public Document deleteDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Remove the stored binary from GridFS
        if (document.getGridFsId() != null && !document.getGridFsId().isBlank()) {
            gridFsTemplate.delete(
                    new Query(Criteria.where("_id").is(new ObjectId(document.getGridFsId())))
            );
            log.info("[DELETE] GridFS file '{}' removed for documentId='{}'",
                     document.getGridFsId(), documentId);
        }

        document.markDeleted();
        return documentRepository.save(document);
    }
}
