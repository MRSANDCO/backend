package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.QueryResponse;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.QueryRepository;
import com.mrs.ca.backend.Repositories.QueryResponseRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import com.mrs.ca.backend.dto.QueryConversationDto;
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
    private final QueryResponseRepository queryResponseRepository;
    private final UserRepository userRepository;
    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    public QueryService(QueryRepository queryRepository,
                        QueryResponseRepository queryResponseRepository,
                        UserRepository userRepository,
                        GridFsTemplate gridFsTemplate,
                        GridFsOperations gridFsOperations,
                        EmailService emailService,
                        WhatsAppService whatsAppService) {
        this.queryRepository = queryRepository;
        this.queryResponseRepository = queryResponseRepository;
        this.userRepository = userRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.gridFsOperations = gridFsOperations;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
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

        // Send WhatsApp notification to the client (async — failure does not affect the response)
        whatsAppService.sendQueryNotification(targetUser, saved);

        return saved;
    }

    /**
     * Raise a query with an optional file attachment.
     * If a file is provided it is stored in GridFS and the query type is set to PDF.
     * If no file is provided a plain TEXT query is created.
     * Allowed MIME types: application/pdf, image/jpeg, image/png.
     */
    public Query raiseQueryWithAttachment(String targetUserId,
                                          String subject,
                                          String message,
                                          MultipartFile file) throws IOException {
        User targetUser = findUserOrThrow(targetUserId);

        Query query = new Query();
        query.setSubject(subject);
        query.setRaisedByAdmin(adminUsername);
        query.setTargetUser(targetUser);
        query.setStatus(Query.QueryStatus.OPEN);

        if (file != null && !file.isEmpty()) {
            String originalFileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "attachment";

            ObjectId gridFsObjectId = gridFsTemplate.store(
                    file.getInputStream(),
                    originalFileName,
                    file.getContentType()
            );

            query.setType(Query.QueryType.PDF);
            query.setGridFsId(gridFsObjectId.toHexString());
            query.setFileName(originalFileName);
            query.setFileSize(file.getSize());
            // Also store message text when provided alongside the file
            if (message != null && !message.isBlank()) {
                query.setMessageText(message);
            }
        } else {
            query.setType(Query.QueryType.TEXT);
            query.setMessageText(message);
        }

        Query saved = queryRepository.save(query);
        log.info("[QUERY] Query '{}' raised by admin for userId='{}' (hasAttachment={})",
                subject, targetUserId, file != null && !file.isEmpty());

        // Send email notification to the client
        emailService.sendQueryNotification(targetUser, saved);

        // Send WhatsApp notification to the client
        whatsAppService.sendQueryNotification(targetUser, saved);

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

        // Send WhatsApp notification to the client (async — failure does not affect the response)
        whatsAppService.sendQueryNotification(targetUser, saved);

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
     * Get a single query by ID for Admin (no ownership check).
     */
    public Query getQueryByIdForAdmin(String queryId) {
        return findQueryOrThrow(queryId);
    }

    /**
     * Delete a query, removing its GridFS file if present and all associated responses.
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

        List<QueryResponse> responses = queryResponseRepository.findByQueryIdOrderByCreatedAtAsc(queryId);
        for (QueryResponse resp : responses) {
            if (resp.getGridFsId() != null && !resp.getGridFsId().isBlank()) {
                gridFsTemplate.delete(
                        new org.springframework.data.mongodb.core.query.Query(
                                Criteria.where("_id").is(new ObjectId(resp.getGridFsId())))
                );
                log.info("[QUERY] GridFS file '{}' removed for responseId='{}'", resp.getGridFsId(), resp.getId());
            }
        }

        queryResponseRepository.deleteByQueryId(queryId);
        log.info("[QUERY] Associated responses deleted for queryId='{}'", queryId);

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
     * Stream a query's file attachment from GridFS to the HTTP response.
     */
    public void streamQueryFile(String queryId, String userId, HttpServletResponse response)
            throws IOException {
        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);

        if (query.getGridFsId() == null || query.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This query has no file attachment.");
        }

        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("_id").is(new ObjectId(query.getGridFsId())))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("Attachment file not found in storage.");
        }

        response.setContentType(resolveContentType(query.getFileName()));
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + query.getFileName() + "\"");

        if (query.getFileSize() != null) {
            response.setContentLengthLong(query.getFileSize());
        }

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }

        log.info("[QUERY] Attachment streamed for queryId='{}' to userId='{}'", queryId, userId);
    }

    // ===================== Response & Conversation Operations =====================

    /**
     * Add a client response/reply to a query without an attachment.
     */
    public QueryResponse addClientResponse(String queryId, String userId, String message) {
        return addClientResponse(queryId, userId, message, null);
    }

    /**
     * Add a client response/reply to a query with an optional document attachment (PDF, Excel, Image, etc.).
     * Validates client ownership, stores the attachment in GridFS (if provided), stores the response in
     * query_responses collection, updates the query status to CLIENT_RESPONDED, and sends an email notification
     * with the attachment to the admin asynchronously.
     */
    public QueryResponse addClientResponse(String queryId, String userId, String message, MultipartFile file) {
        boolean hasMessage = message != null && !message.trim().isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasMessage && !hasFile) {
            throw new IllegalArgumentException("Response message or attachment must be provided.");
        }

        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);

        String gridFsId = null;
        String fileName = null;
        Long fileSize = null;
        String fileType = null;

        if (hasFile) {
            fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
            fileType = file.getContentType() != null ? file.getContentType() : resolveContentType(fileName);
            fileSize = file.getSize();
            try {
                ObjectId storedId = gridFsTemplate.store(file.getInputStream(), fileName, fileType);
                gridFsId = storedId.toHexString();
                log.info("[QUERY] Client response attachment '{}' stored with GridFS ID '{}'", fileName, gridFsId);
            } catch (IOException e) {
                log.error("[QUERY] Failed to store attachment for client queryId='{}': {}", queryId, e.getMessage(), e);
                throw new RuntimeException("Failed to store attachment: " + e.getMessage(), e);
            }
        }

        String effectiveMessage = hasMessage ? message.trim() : "";

        QueryResponse response = new QueryResponse(
                query.getId(),
                user.getUserId(),
                user.getUserId(),
                user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUserId(),
                user.getEmail(),
                QueryResponse.SenderRole.CLIENT,
                effectiveMessage,
                gridFsId,
                fileName,
                fileSize,
                fileType
        );

        QueryResponse savedResponse = queryResponseRepository.save(response);
        log.info("[QUERY] Client '{}' replied to queryId='{}', responseId='{}' (hasAttachment={})",
                userId, queryId, savedResponse.getId(), gridFsId != null);

        // Update query status to CLIENT_RESPONDED
        query.setStatus(Query.QueryStatus.CLIENT_RESPONDED);
        queryRepository.save(query);

        // Notify Admin via Resend Email (Async)
        emailService.sendClientResponseNotification(user, query, savedResponse);

        return savedResponse;
    }

    /**
     * Add an admin response/reply to a query without an attachment.
     * Updates the query status to ADMIN_RESPONDED.
     */
    public QueryResponse addAdminResponse(String queryId, String adminUsernameParam, String message) {
        return addAdminResponse(queryId, adminUsernameParam, message, null);
    }

    /**
     * Add an admin response/reply to a query with an optional attachment.
     * Updates the query status to ADMIN_RESPONDED.
     */
    public QueryResponse addAdminResponse(String queryId, String adminUsernameParam, String message, MultipartFile file) {
        boolean hasMessage = message != null && !message.trim().isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasMessage && !hasFile) {
            throw new IllegalArgumentException("Response message or attachment must be provided.");
        }

        Query query = findQueryOrThrow(queryId);

        String effectiveAdmin = (adminUsernameParam != null && !adminUsernameParam.isBlank())
                ? adminUsernameParam
                : (adminUsername != null && !adminUsername.isBlank() ? adminUsername : "admin");

        String clientId = query.getTargetUser() != null ? query.getTargetUser().getUserId() : null;
        String senderDisplayName = "Admin (" + effectiveAdmin + ")";

        String gridFsId = null;
        String fileName = null;
        Long fileSize = null;
        String fileType = null;

        if (hasFile) {
            fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
            fileType = file.getContentType() != null ? file.getContentType() : resolveContentType(fileName);
            fileSize = file.getSize();
            try {
                ObjectId storedId = gridFsTemplate.store(file.getInputStream(), fileName, fileType);
                gridFsId = storedId.toHexString();
                log.info("[QUERY] Admin response attachment '{}' stored with GridFS ID '{}'", fileName, gridFsId);
            } catch (IOException e) {
                log.error("[QUERY] Failed to store attachment for admin queryId='{}': {}", queryId, e.getMessage(), e);
                throw new RuntimeException("Failed to store attachment: " + e.getMessage(), e);
            }
        }

        String effectiveMessage = hasMessage ? message.trim() : "";

        QueryResponse response = new QueryResponse(
                query.getId(),
                clientId,
                effectiveAdmin,
                senderDisplayName,
                null,
                QueryResponse.SenderRole.ADMIN,
                effectiveMessage,
                gridFsId,
                fileName,
                fileSize,
                fileType
        );

        QueryResponse savedResponse = queryResponseRepository.save(response);
        log.info("[QUERY] Admin '{}' replied to queryId='{}', responseId='{}' (hasAttachment={})",
                effectiveAdmin, queryId, savedResponse.getId(), gridFsId != null);

        // Update query status to ADMIN_RESPONDED
        query.setStatus(Query.QueryStatus.ADMIN_RESPONDED);
        queryRepository.save(query);

        return savedResponse;
    }

    /**
     * Stream a response's file attachment from GridFS to the HTTP response for a client.
     */
    public void streamResponseFile(String queryId, String responseId, String userId, HttpServletResponse response)
            throws IOException {
        User user = findUserOrThrow(userId);
        Query query = findQueryOrThrow(queryId);
        validateOwnership(query, user, queryId, userId);

        QueryResponse queryResponse = queryResponseRepository.findById(responseId)
                .orElseThrow(() -> new IllegalArgumentException("Response '" + responseId + "' not found"));

        if (!query.getId().equals(queryResponse.getQueryId())) {
            throw new IllegalArgumentException("Response does not belong to the specified query.");
        }

        if (queryResponse.getGridFsId() == null || queryResponse.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This response has no file attachment.");
        }

        streamFromGridFs(queryResponse.getGridFsId(), queryResponse.getFileName(), queryResponse.getFileSize(), response);
        log.info("[QUERY] Response attachment streamed for responseId='{}', queryId='{}' to userId='{}'",
                responseId, queryId, userId);
    }

    /**
     * Stream a response's file attachment from GridFS to the HTTP response for Admin.
     */
    public void streamResponseFileForAdmin(String queryId, String responseId, HttpServletResponse response)
            throws IOException {
        Query query = findQueryOrThrow(queryId);

        QueryResponse queryResponse = queryResponseRepository.findById(responseId)
                .orElseThrow(() -> new IllegalArgumentException("Response '" + responseId + "' not found"));

        if (!query.getId().equals(queryResponse.getQueryId())) {
            throw new IllegalArgumentException("Response does not belong to the specified query.");
        }

        if (queryResponse.getGridFsId() == null || queryResponse.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This response has no file attachment.");
        }

        streamFromGridFs(queryResponse.getGridFsId(), queryResponse.getFileName(), queryResponse.getFileSize(), response);
        log.info("[QUERY] Admin downloaded response attachment for responseId='{}', queryId='{}'",
                responseId, queryId);
    }

    /**
     * Get the full conversation thread for a query (the query details and all responses in chronological order).
     */
    public QueryConversationDto getQueryConversation(String queryId, String userId, boolean isAdmin) {
        Query query;
        if (!isAdmin) {
            User user = findUserOrThrow(userId);
            query = findQueryOrThrow(queryId);
            validateOwnership(query, user, queryId, userId);
        } else {
            query = findQueryOrThrow(queryId);
        }

        List<QueryResponse> responses = queryResponseRepository.findByQueryIdOrderByCreatedAtAsc(queryId);
        return new QueryConversationDto(query, responses);
    }

    /**
     * Get all responses for a query ordered chronologically.
     */
    public List<QueryResponse> getQueryResponses(String queryId, String userId, boolean isAdmin) {
        if (!isAdmin) {
            User user = findUserOrThrow(userId);
            Query query = findQueryOrThrow(queryId);
            validateOwnership(query, user, queryId, userId);
        } else {
            findQueryOrThrow(queryId);
        }

        return queryResponseRepository.findByQueryIdOrderByCreatedAtAsc(queryId);
    }

    /**
     * Update query status (e.g. mark as RESOLVED, CLOSED, etc.).
     */
    public Query updateQueryStatus(String queryId, Query.QueryStatus newStatus) {
        Query query = findQueryOrThrow(queryId);
        query.setStatus(newStatus);
        Query saved = queryRepository.save(query);
        log.info("[QUERY] Query '{}' status updated to '{}'", queryId, newStatus);
        return saved;
    }

    // ===================== Admin — stream attachment (no ownership check) =====================

    /**
     * Admin can stream any query's file attachment without ownership check.
     */
    public void streamQueryFileForAdmin(String queryId, HttpServletResponse response)
            throws IOException {
        Query query = findQueryOrThrow(queryId);

        if (query.getGridFsId() == null || query.getGridFsId().isBlank()) {
            throw new IllegalArgumentException("This query has no file attachment.");
        }

        streamFromGridFs(query.getGridFsId(), query.getFileName(), query.getFileSize(), response);
        log.info("[QUERY] Admin downloaded attachment for queryId='{}'", queryId);
    }

    private void streamFromGridFs(String gridFsId, String fileName, Long fileSize, HttpServletResponse response)
            throws IOException {
        GridFSFile gridFSFile = gridFsTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("_id").is(new ObjectId(gridFsId)))
        );

        if (gridFSFile == null) {
            throw new IllegalArgumentException("Attachment file not found in storage.");
        }

        response.setContentType(resolveContentType(fileName));
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + (fileName != null ? fileName : "attachment") + "\"");

        if (fileSize != null) {
            response.setContentLengthLong(fileSize);
        }

        try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    // ===================== Helpers =====================

    /**
     * Derive the HTTP Content-Type from a filename's extension.
     * Supports PDF, Excel (.xlsx, .xls, .csv), Images (.jpg, .jpeg, .png), Word docs, etc.
     * Falls back to application/octet-stream for unknown types.
     */
    public static String resolveContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls"))  return "application/vnd.ms-excel";
        if (lower.endsWith(".csv"))  return "text/csv";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc"))  return "application/msword";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".zip"))  return "application/zip";
        return "application/octet-stream";
    }

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

