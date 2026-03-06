package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;

    public UserService(UserRepository userRepository,
                       DocumentRepository documentRepository,
                       PasswordEncoder passwordEncoder,
                       GridFsTemplate gridFsTemplate,
                       GridFsOperations gridFsOperations) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
        this.gridFsTemplate = gridFsTemplate;
        this.gridFsOperations = gridFsOperations;
    }

    // ===================== Document Access =====================

    /**
     * Get all ACTIVE documents owned by this user.
     */
    public List<Document> getMyDocuments(String userId) {
        User user = findUserOrThrow(userId);
        return documentRepository.findByOwnerUserIdAndStatus(user.getId(), DocumentStatus.ACTIVE);
    }

    /**
     * Get a specific document only if it belongs to this user.
     */
    public Document getDocumentById(String documentId, String userId) {
        User user = findUserOrThrow(userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Ownership check — user can only see their own documents
        if (document.getOwnerUser() == null ||
                !document.getOwnerUser().getId().equals(user.getId())) {
            log.warn("[ACCESS DENIED] userId='{}' attempted to access documentId='{}' owned by another user",
                     userId, documentId);
            throw new SecurityException("Access denied: document does not belong to this user");
        }

        return document;
    }

    /**
     * Stream a document's binary from GridFS directly to the HTTP response.
     */
    public void streamDocument(String documentId, String userId, HttpServletResponse response)
            throws IOException {

        Document document = getDocumentById(documentId, userId);

        if (document.getGridFsId() == null || document.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("No stored file found for this document");
        }

        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(document.getGridFsId())))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("File not found in storage");
        }

        String contentType = document.getFileType() != null
                ? document.getFileType()
                : "application/octet-stream";

        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + document.getFileName() + "\"");

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    // ===================== Profile =====================

    public User getProfile(String userId) {
        return findUserOrThrow(userId);
    }

    // ===================== User Auth =====================

    public Optional<User> authenticateUser(String userId, String password) {
        Optional<User> user = userRepository.findByUserId(userId);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())
                && user.get().isActive()) {
            return user;
        }
        log.warn("[AUTH] Failed user login attempt for userId='{}'", userId);
        return Optional.empty();
    }

    // ===================== Helpers =====================

    private User findUserOrThrow(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));
    }
}
