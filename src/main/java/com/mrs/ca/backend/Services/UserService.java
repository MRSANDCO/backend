package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.*;
import com.mrs.ca.backend.Repositories.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       DocumentRepository documentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
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
            throw new SecurityException("Access denied: document does not belong to this user");
        }

        return document;
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
        return Optional.empty();
    }

    // ===================== Helpers =====================

    private User findUserOrThrow(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));
    }
}
