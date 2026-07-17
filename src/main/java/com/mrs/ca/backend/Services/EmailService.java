package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public EmailService(org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    /**
     * Sends a query-raised notification email to the target client.
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

        if (mailSender == null) {
            log.warn("[EMAIL] JavaMailSender is not initialized. Please verify spring.mail properties are configured. Skipping email notification.");
            return;
        }

        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("[EMAIL] MAIL_USERNAME is not configured. Skipping email notification.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "MRS & Co. — Chartered Accountants");
            helper.setTo(targetUser.getEmail());
            helper.setSubject("📋 New Query Raised: " + query.getSubject());
            helper.setText(buildHtmlEmail(targetUser, query), true);

            mailSender.send(message);
            log.info("[EMAIL] Query notification sent to '{}' for queryId='{}'",
                    targetUser.getEmail(), query.getId());

        } catch (MessagingException e) {
            log.error("[EMAIL] Failed to send query notification to '{}': {}",
                    targetUser.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[EMAIL] Unexpected error sending notification: {}", e.getMessage(), e);
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
        String messagePreview = (query.getMessageText() != null && !query.getMessageText().isBlank())
                ? "<p style=\"font-size:14px;color:#374151;line-height:1.7;margin:0 0 24px;\">"
                  + escapeHtml(query.getMessageText()).substring(0, Math.min(query.getMessageText().length(), 300))
                  + (query.getMessageText().length() > 300 ? "…" : "")
                  + "</p>"
                : "<p style=\"font-size:13px;color:#9ca3af;font-style:italic;margin:0 0 24px;\">A PDF document has been attached to this query.</p>";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>New Query from MRS &amp; Co.</title>
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
                              MRS &amp; Co. — Chartered Accountants
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
                              Our team at <strong>MRS &amp; Co.</strong> has raised a new query
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
                messagePreview,
                queryTypeBadge,
                raisedAt,
                loginUrl,
                loginUrl,
                loginUrl
        );
    }

    public String sendTestEmail(String recipient) {
        if (mailSender == null) {
            return "Failure: JavaMailSender is not initialized (null). Please check spring.mail property names.";
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            return "Failure: MAIL_USERNAME is not configured (blank or null).";
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, "MRS & Co. (Test Connection)");
            helper.setTo(recipient);
            helper.setSubject("📋 Test Connection from MRS & Co. Backend");
            helper.setText("<h3>SMTP test configuration: Success!</h3><p>If you see this, the Spring Mail configuration is working perfectly.</p>", true);
            mailSender.send(message);
            return "Success: Email sent successfully from " + senderEmail + " to " + recipient;
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
