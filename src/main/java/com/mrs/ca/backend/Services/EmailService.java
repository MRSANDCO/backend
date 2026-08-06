package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${app.resend.api-key:}")
    private String resendApiKey;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private GridFsOperations gridFsOperations;

    private String getAttachmentBase64(String gridFsId) {
        if (gridFsId == null || gridFsId.isBlank()) {
            return null;
        }
        try {
            GridFSFile gridFSFile = gridFsTemplate.findOne(
                    new org.springframework.data.mongodb.core.query.Query(
                            Criteria.where("_id").is(new ObjectId(gridFsId)))
            );

            if (gridFSFile == null) {
                log.warn("[EMAIL] GridFS file not found for gridFsId='{}'", gridFsId);
                return null;
            }

            try (var inputStream = gridFsOperations.getResource(gridFSFile).getInputStream()) {
                byte[] bytes = StreamUtils.copyToByteArray(inputStream);
                return Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            log.error("[EMAIL] Failed to read attachment from GridFS for gridFsId='{}': {}",
                    gridFsId, e.getMessage(), e);
            return null;
        }
    }

    @jakarta.annotation.PostConstruct
public void logKeyDebug() {
    if (resendApiKey == null || resendApiKey.isBlank()) {
        log.warn("[EMAIL-DEBUG] resendApiKey is NULL or BLANK");
    } else {
        String masked = resendApiKey.length() > 10
                ? resendApiKey.substring(0, 6) + "..." + resendApiKey.substring(resendApiKey.length() - 4)
                : "TOO_SHORT:" + resendApiKey;
        log.warn("[EMAIL-DEBUG] resendApiKey loaded, length={}, value={}", resendApiKey.length(), masked);
    }
}

    @Value("${app.resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.resend.from-name:MRS & Co. Chartered Accountants}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    private final RestClient restClient = RestClient.create();

    /**
     * Sends a query-raised notification email to the target client via Resend.
     * Called asynchronously so it never blocks the HTTP response.
     * All exceptions are caught and logged — email failure never breaks query creation.
     */
    @Async
    public void sendQueryNotification(User targetUser, Query query) {
        if (targetUser.getEmail() == null || targetUser.getEmail().isBlank()) {
            log.warn("[EMAIL] Skipping notification for userId='{}' — no email address on record.",
                    targetUser.getUserId());
            return;
        }

        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("[EMAIL] RESEND_API_KEY is not configured. Skipping email notification.");
            return;
        }

        try {
            String subject = "📋 New Query Raised: " + query.getSubject();
            String html = buildHtmlEmail(targetUser, query);

            String attachmentFilename = query.getFileName();
            String attachmentBase64 = null;
            if (query.getGridFsId() != null && !query.getGridFsId().isBlank()) {
                attachmentBase64 = getAttachmentBase64(query.getGridFsId());
            }

            sendViaResend(targetUser.getEmail(), subject, html, attachmentFilename, attachmentBase64);

            log.info("[EMAIL] Query notification sent to '{}' for queryId='{}'",
                    targetUser.getEmail(), query.getId());

        } catch (Exception e) {
            log.error("[EMAIL] Failed to send query notification to '{}': {}",
                    targetUser.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Low-level call to the Resend API. Throws on failure — caller decides how to handle it.
     */
    private void sendViaResend(String to, String subject, String html) {
        sendViaResend(to, subject, html, null, null);
    }

    private void sendViaResend(String to, String subject, String html, String attachmentFilename, String attachmentBase64) {
        Map<String, Object> payload;
        if (attachmentFilename != null && attachmentBase64 != null) {
            Map<String, String> attachment = Map.of(
                    "filename", attachmentFilename,
                    "content", attachmentBase64
            );
            payload = Map.of(
                    "from", fromName + " <" + fromEmail + ">",
                    "to", List.of(to),
                    "subject", subject,
                    "html", html,
                    "attachments", List.of(attachment)
            );
        } else {
            payload = Map.of(
                    "from", fromName + " <" + fromEmail + ">",
                    "to", List.of(to),
                    "subject", subject,
                    "html", html
            );
        }

        try {
            restClient.post()
                    .uri(RESEND_API_URL)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            throw new RuntimeException("Resend API call failed [" + status + "]: " + body, e);
        }
    }

    // ── HTML Email Template ───────────────────────────────────────────────────

    private String buildHtmlEmail(User targetUser, Query query) {
        String clientName   = targetUser.getFullName() != null ? targetUser.getFullName() : targetUser.getUserId();
        String subject      = query.getSubject();
        String queryType    = query.getType() != null ? query.getType().name() : "TEXT";
        String queryTypeBadge = "PDF".equals(queryType)
                ? "<span style=\"background:#f59e0b;color:#fff;padding:2px 10px;border-radius:99px;font-size:11px;font-weight:700;letter-spacing:0.06em;\">PDF</span>"
                : "<span style=\"background:#6366f1;color:#fff;padding:2px 10px;border-radius:99px;font-size:11px;font-weight:700;letter-spacing:0.06em;\">TEXT</span>";
        String raisedAt     = query.getCreatedAt() != null ? query.getCreatedAt().format(DATE_FMT) : "Just now";
        String loginUrl     = frontendUrl.replaceAll("/$", "") + "/login";
        String messagePreview;
        if (query.getMessageText() != null && !query.getMessageText().isBlank()) {
            // Show message text (also applies to mixed text+attachment queries)
            messagePreview = "<p style=\"font-size:14px;color:#374151;line-height:1.7;margin:0 0 12px;\">"
                    + escapeHtml(query.getMessageText())
                    + "</p>";
        } else {
            // PDF-only query
            messagePreview = "<p style=\"font-size:13px;color:#9ca3af;font-style:italic;margin:0 0 12px;\">A document has been attached to this query.</p>";
        }

        // Attachment badge (shown when a file is present)
        String attachmentRow = "";
        if (query.getFileName() != null && !query.getFileName().isBlank() && query.getId() != null) {
            attachmentRow = "<div style=\"margin-top:12px;\">"
                    + "<div style=\"display:flex;align-items:center;gap:8px;"
                    + "padding:10px 14px;background:#e0f2fe;border:1px solid #bae6fd;border-radius:8px;"
                    + "font-size:12px;color:#0369a1;margin-bottom:8px;\">\n"
                    + "<span style=\"font-size:16px;\">📎</span>\n"
                    + "<span><strong>Attachment (Find below the mail):</strong> " + escapeHtml(query.getFileName()) + "</span>\n"
                    + "</div>\n"
                    + "</div>";
        }

        // Combine message + attachment block
        String messageBlock = messagePreview + attachmentRow;

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>New Query from MRS &amp; Co. Chartered Accountants</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">

                  <!-- Outer wrapper -->
                  <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                         style="background:#f1f5f9;padding:40px 16px;">
                    <tr><td align="center">

                      <!-- Card -->
                      <table role="presentation" cellpadding="0" cellspacing="0" width="600"
                             style="max-width:600px;width:100%%;background:#ffffff;border-radius:16px;
                                    overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#0f172a 0%%,#1e3a5f 100%%);
                                     padding:36px 40px;text-align:center;">
                            <div style="display:inline-block;background:rgba(255,255,255,0.12);
                                        border-radius:50%%;padding:12px;margin-bottom:16px;">
                              <div style="width:40px;height:40px;background:#ffffff;border-radius:50%%;
                                          display:flex;align-items:center;justify-content:center;margin:auto;">
                                <span style="font-size:20px;">📋</span>
                              </div>
                            </div>
                            <h1 style="margin:0 0 4px;color:#ffffff;font-size:22px;font-weight:700;
                                       letter-spacing:-0.3px;">New Query Raised</h1>
                            <p style="margin:0;color:#93c5fd;font-size:13px;letter-spacing:0.04em;">
                              MRS &amp; Co. Chartered Accountants
                            </p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:40px;">

                            <p style="font-size:15px;color:#1f2937;margin:0 0 24px;">
                              Dear <strong>%s</strong>,
                            </p>
                            <p style="font-size:14px;color:#4b5563;line-height:1.7;margin:0 0 28px;">
                              Our team at <strong>MRS &amp; Co. Chartered Accountants</strong> has raised a new query
                              on your account. Please log in to your dashboard to view the
                              full details and respond at your earliest convenience.
                            </p>

                            <!-- Query details box -->
                            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                                   style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;
                                          margin-bottom:32px;">
                              <tr>
                                <td style="padding:24px;">
                                  <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                                    <tr>
                                      <td style="padding-bottom:14px;border-bottom:1px solid #e2e8f0;">
                                        <p style="margin:0 0 4px;font-size:11px;color:#94a3b8;
                                                   text-transform:uppercase;letter-spacing:0.08em;
                                                   font-weight:600;">Subject</p>
                                        <p style="margin:0;font-size:16px;color:#0f172a;font-weight:700;">%s</p>
                                      </td>
                                    </tr>
                                    <tr>
                                      <td style="padding-top:14px;padding-bottom:14px;border-bottom:1px solid #e2e8f0;">
                                        <p style="margin:0 0 6px;font-size:11px;color:#94a3b8;
                                                   text-transform:uppercase;letter-spacing:0.08em;
                                                   font-weight:600;">Message</p>
                                        %s
                                      </td>
                                    </tr>
                                    <tr>
                                      <td style="padding-top:14px;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                                          <tr>
                                            <td style="width:50%%;">
                                              <p style="margin:0 0 4px;font-size:11px;color:#94a3b8;
                                                         text-transform:uppercase;letter-spacing:0.08em;
                                                         font-weight:600;">Type</p>
                                              %s
                                            </td>
                                            <td style="width:50%%;">
                                              <p style="margin:0 0 4px;font-size:11px;color:#94a3b8;
                                                         text-transform:uppercase;letter-spacing:0.08em;
                                                         font-weight:600;">Raised On</p>
                                              <p style="margin:0;font-size:13px;color:#1f2937;font-weight:500;">%s</p>
                                            </td>
                                          </tr>
                                        </table>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                            </table>

                            <!-- CTA Button -->
                            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                                   style="margin-bottom:32px;">
                              <tr>
                                <td align="center">
                                  <a href="%s"
                                     style="display:inline-block;background:linear-gradient(135deg,#2563eb,#1d4ed8);
                                            color:#ffffff;text-decoration:none;font-size:15px;font-weight:700;
                                            padding:14px 40px;border-radius:10px;
                                            box-shadow:0 4px 14px rgba(37,99,235,0.35);
                                            letter-spacing:0.02em;">
                                    🔐 View Query on Dashboard
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <p style="font-size:12px;color:#94a3b8;text-align:center;
                                       border-top:1px solid #e2e8f0;padding-top:20px;margin:0;">
                              If the button doesn't work, copy and paste this link into your browser:<br/>
                              <a href="%s" style="color:#2563eb;word-break:break-all;">%s</a>
                            </p>

                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f8fafc;border-top:1px solid #e2e8f0;
                                     padding:20px 40px;text-align:center;">
                            <p style="margin:0 0 4px;font-size:12px;color:#64748b;">
                              This is an automated notification from <strong>MRS &amp; Co. Chartered Accountants</strong>.
                            </p>
                            <p style="margin:0;font-size:11px;color:#94a3b8;">
                              Please do not reply to this email. Log in to your dashboard to respond.
                            </p>
                          </td>
                        </tr>

                      </table>
                      <!-- /Card -->

                    </td></tr>
                  </table>
                  <!-- /Outer wrapper -->

                </body>
                </html>
                """.formatted(
                escapeHtml(clientName),
                escapeHtml(subject),
                messageBlock,
                queryTypeBadge,
                raisedAt,
                loginUrl,
                loginUrl,
                loginUrl
        );
    }

    public String sendTestEmail(String recipient) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            return "Failure: RESEND_API_KEY is not configured (blank or null).";
        }
        try {
            sendViaResend(
                    recipient,
                    "📋 Test Connection from MRS & Co. Chartered Accountants",
                    "<h3>Resend configuration: Success!</h3><p>If you see this, the Resend integration is working perfectly.</p>"
            );
            return "Success: Email sent successfully from " + fromEmail + " to " + recipient;
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return "Failure: " + e.getMessage() + "\n\nStacktrace:\n" + sw.toString();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}