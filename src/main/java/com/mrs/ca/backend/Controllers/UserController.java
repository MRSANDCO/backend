package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.QueryResponse;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.QueryService;
import com.mrs.ca.backend.Services.UserService;
import com.mrs.ca.backend.dto.QueryConversationDto;
import com.mrs.ca.backend.dto.QueryResponseRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final QueryService queryService;

    public UserController(UserService userService, QueryService queryService) {
        this.userService = userService;
        this.queryService = queryService;
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
    public void downloadDocument(@PathVariable String userId,
                                 @PathVariable String documentId,
                                 HttpServletResponse response) throws IOException {
        if (!isAuthorized(userId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }
        try {
            userService.streamDocument(documentId, userId, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
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

    // ===================== Queries (raised by Admin) =====================

    /**
     * List all queries raised for this user by the admin.
     */
    @GetMapping("/{userId}/queries")
    public ResponseEntity<?> getMyQueries(@PathVariable String userId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            List<Query> queries = queryService.getQueriesForUser(userId);
            return ResponseEntity.ok(queries);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a single query by ID and mark it as SEEN.
     */
    @GetMapping("/{userId}/queries/{queryId}")
    public ResponseEntity<?> getQuery(@PathVariable String userId,
                                      @PathVariable String queryId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            // Fetch the query, then mark as seen in one step
            Query query = queryService.markSeen(queryId, userId);
            return ResponseEntity.ok(query);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the complete conversation thread for a query (query + ordered responses).
     * Also automatically marks the query as SEEN if it was OPEN.
     */
    @GetMapping("/{userId}/queries/{queryId}/conversation")
    public ResponseEntity<?> getQueryConversation(@PathVariable String userId,
                                                  @PathVariable String queryId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            queryService.markSeen(queryId, userId);
            QueryConversationDto conversation = queryService.getQueryConversation(queryId, userId, false);
            return ResponseEntity.ok(conversation);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all responses for a query.
     */
    @GetMapping("/{userId}/queries/{queryId}/responses")
    public ResponseEntity<?> getQueryResponses(@PathVariable String userId,
                                               @PathVariable String queryId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            List<QueryResponse> responses = queryService.getQueryResponses(queryId, userId, false);
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit a client response/reply to an existing query via JSON (text only).
     */
    @PostMapping(value = "/{userId}/queries/{queryId}/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitJsonResponse(
            @PathVariable String userId,
            @PathVariable String queryId,
            @RequestBody QueryResponseRequest request) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));

        if (request == null || request.getMessage() == null || request.getMessage().trim().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }

        try {
            QueryResponse response = queryService.addClientResponse(queryId, userId, request.getMessage(), null);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Response submitted successfully",
                    "response", response
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit a client response/reply to an existing query with an optional attachment (PDF, Excel, Image, etc.).
     */
    @PostMapping(value = "/{userId}/queries/{queryId}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitMultipartResponse(
            @PathVariable String userId,
            @PathVariable String queryId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));

        boolean hasMessage = message != null && !message.trim().isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasMessage && !hasFile) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message or attachment must be provided"));
        }

        try {
            QueryResponse response = queryService.addClientResponse(queryId, userId, message, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Response submitted successfully",
                    "response", response
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Explicitly mark a query as SEEN.
     */
    @PutMapping("/{userId}/queries/{queryId}/seen")
    public ResponseEntity<?> markQuerySeen(@PathVariable String userId,
                                           @PathVariable String queryId) {
        if (!isAuthorized(userId)) return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        try {
            Query query = queryService.markSeen(queryId, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Query marked as seen",
                    "queryId", query.getId(),
                    "status", query.getStatus().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download the attachment of a query.
     */
    @GetMapping("/{userId}/queries/{queryId}/download")
    public void downloadQueryFile(@PathVariable String userId,
                                  @PathVariable String queryId,
                                  HttpServletResponse response) throws IOException {
        if (!isAuthorized(userId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }
        try {
            queryService.streamQueryFile(queryId, userId, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Download the attachment of a query response.
     */
    @GetMapping("/{userId}/queries/{queryId}/responses/{responseId}/download")
    public void downloadResponseAttachment(@PathVariable String userId,
                                           @PathVariable String queryId,
                                           @PathVariable String responseId,
                                           HttpServletResponse response) throws IOException {
        if (!isAuthorized(userId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }
        try {
            queryService.streamResponseFile(queryId, responseId, userId, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }
}
