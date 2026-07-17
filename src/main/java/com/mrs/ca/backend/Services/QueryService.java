package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.QueryRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    @Value("${app.admin.username}")
    private String adminUsername;

    private final QueryRepository queryRepository;
    private final UserRepository userRepository;
    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final EmailService emailService;

    public QueryService(QueryRepository queryRepository,
                        UserRepository userRepository,
                        GridFsTemplate gridFsTemplate,
                        GridFsOperations gridFsOperations,
                        EmailService emailService) {
        this.queryRepository = queryRepository;
        this.userRepository = userRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.gridFsOperations = gridFsOperations;
        this.emailService = emailService;
    }

    // ===================== Admin Operations =====================

    /**
     * Raise a TEXT query to a specific user/company.
     */
    public Query raiseTextQuery(String targetUserId, String subject, String message) {
        User targetUser = findUserOrThrow(targetUserId);

        Query query = new Query();
        query.setSubject(subject);
        query.setMessageText(message);
        query.setType(Query.QueryType.TEXT);
        query.setRaisedByAdmin(adminUsername);
        query.setTargetUser(targetUser);
        query.setStatus(Query.QueryStatus.OPEN);

        Query saved = queryRepository.save(query);
        log.info("[QUERY] Text query '{}' raised by admin for userId='{}'", subject, targetUserId);

        // Send email notification to the client (async — failure does not affect the response)
        emailService.sendQueryNotification(targetUser, saved);

        return saved;
    }

    /**
     * Raise a PDF query to a specific user/company.
     * The PDF is stored in GridFS.
     */
    public Query raisePdfQuery(String targetUserId, String subject, MultipartFile file) throws IOException {
        User targetUser = findUserOrThrow(targetUserId);

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "query.pdf";

        // Store PDF in GridFS
        ObjectId gridFsObjectId = gridFsTemplate.store(
                file.getInputStream(),
                originalFileName,
                file.getContentType()
        );

        Query query = new Query();
        query.setSubject(subject);
        query.setType(Query.QueryType.PDF);
        query.setGridFsId(gridFsObjectId.toHexString());
        query.setFileName(originalFileName);
        query.setFileSize(file.getSize());
        query.setRaisedByAdmin(adminUsername);
        query.setTargetUser(targetUser);
        query.setStatus(Query.QueryStatus.OPEN);

        Query saved = queryRepository.save(query);
        log.info("[QUERY] PDF query '{}' raised by admin for userId='{}', gridFsId='{}'",
                subject, targetUserId, gridFsObjectId.toHexString());

        // Send email notification to the client (async — failure does not affect the response)
        emailService.sendQueryNotification(targetUser, saved);

        return saved;
    }

    /**
     * List all queries (admin view). Optionally filter by userId.
     */
    public List<Query> getAllQueries(String userId) {
        if (userId != null && !userId.isBlank()) {
            User user = findUserOrThrow(userId);
            return queryRepository.findByTargetUserIdOrderByCreatedAtDesc(user.getId());
        }
        return queryRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Delete a query, removing its GridFS file if present.
     */
    public void deleteQuery(String queryId) {
        Query query = findQueryOrThrow(queryId);

        if (query.getGridFsId() != null && !query.getGridFsId().isBlank()) {
            gridFsTemplate.delete(
                    new org.springframework.data.mongodb.core.query.Query(
                            Criteria.where("_id").is(new ObjectId(query.getGridFsId())))
            );
            log.info("[QUERY] GridFS file '{}' removed for queryId='{}'", query.getGridFsId(), queryId);
        }

        queryRepository.delete(query);
        log.info("[QUERY] Query '{}' deleted by admin.", queryId);
    }

    // ===================== User / Client Operations =====================

    /**
     * Get all queries raised for a specific user (by their userId string, not MongoDB _id).
     */
    public List<Query> getQueriesForUser(String userId) {
        User user = findUserOrThrow(userId);
        return queryRepository.findByTargetUserIdOrderByCreatedAtDesc(user.getId());
    }

    /**
     * Get a single query — validates ownership.
     */
    public Query getQueryById(String queryId, String userId) {
        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);
        return query;
    }

    /**
     * Mark a query as SEEN when the user opens it.
     */
    public Query markSeen(String queryId, String userId) {
        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);

        if (query.getStatus() == Query.QueryStatus.OPEN) {
            query.setStatus(Query.QueryStatus.SEEN);
            query = queryRepository.save(query);
            log.info("[QUERY] QueryId='{}' marked as SEEN by userId='{}'", queryId, userId);
        }
        return query;
    }

    /**
     * Stream a query's PDF attachment from GridFS to the HTTP response.
     */
    public void streamQueryFile(String queryId, String userId, HttpServletResponse response)
            throws IOException {
        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);

        if (query.getType() != Query.QueryType.PDF || query.getGridFsId() == null || query.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This query has no PDF attachment.");
        }

        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("_id").is(new ObjectId(query.getGridFsId())))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("PDF file not found in storage.");
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + query.getFileName() + "\"");

        if (query.getFileSize() != null) {
            response.setContentLengthLong(query.getFileSize());
        }

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }

        log.info("[QUERY] PDF streamed for queryId='{}' to userId='{}'", queryId, userId);
    }

    // ===================== Admin — stream PDF (no ownership check) =====================

    /**
     * Admin can stream any query's PDF without ownership check.
     */
    public void streamQueryFileForAdmin(String queryId, HttpServletResponse response)
            throws IOException {
        Query query = findQueryOrThrow(queryId);

        if (query.getType() != Query.QueryType.PDF || query.getGridFsId() == null || query.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This query has no PDF attachment.");
        }

        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("_id").is(new ObjectId(query.getGridFsId())))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("PDF file not found in storage.");
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + query.getFileName() + "\"");

        if (query.getFileSize() != null) {
            response.setContentLengthLong(query.getFileSize());
        }

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }

        log.info("[QUERY] Admin downloaded PDF for queryId='{}'", queryId);
    }

    // ===================== Helpers =====================

    private User findUserOrThrow(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User '" + userId + "' not found"));
    }

    private Query findQueryOrThrow(String queryId) {
        return queryRepository.findById(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query '" + queryId + "' not found"));
    }

    private void validateOwnership(Query query, User user, String queryId, String userId) {
        if (query.getTargetUser() == null ||
                !query.getTargetUser().getId().equals(user.getId())) {
            log.warn("[ACCESS DENIED] userId='{}' attempted to access queryId='{}' belonging to another user",
                    userId, queryId);
            throw new SecurityException("Access denied: query does not belong to this user");
        }
    }

    public String testEmailConfiguration(String recipient) {
        return emailService.sendTestEmail(recipient);
    }
}

