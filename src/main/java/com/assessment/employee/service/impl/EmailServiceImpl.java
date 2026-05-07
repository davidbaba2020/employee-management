package com.assessment.employee.service.impl;

import com.assessment.employee.dto.response.ImportResultDto;
import com.assessment.employee.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * HTML email notification service using Thymeleaf templates and JavaMail MimeMessage.
 *
 * <p>All public methods are annotated with {@code @Async} so they run off the
 * HTTP request thread and never affect transaction commit/rollback.
 *
 * <p>Dev mode ({@code app.email.enabled=false}): logs intent only, no SMTP calls.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private static final String COMPANY_NAME = "Acme Corporation";

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:noreply@employeemgmt.com}")
    private String fromAddress;

    @Value("${app.email.from-name:Employee Management System}")
    private String fromName;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    // =========================================================================
    // Welcome Email
    // =========================================================================

    @Override
    @Async("taskExecutor")
    public void sendWelcomeEmail(String toEmail, String employeeName) {
        Context ctx = new Context();
        ctx.setVariable("employeeName", employeeName);
        ctx.setVariable("companyName", COMPANY_NAME);

        String html = templateEngine.process("email/welcome", ctx);
        sendHtml(toEmail, "Welcome to " + COMPANY_NAME + "! 🎉", html, "welcome");
    }

    // =========================================================================
    // Deactivation Email
    // =========================================================================

    @Override
    @Async("taskExecutor")
    public void sendDeactivationEmail(String toEmail, String employeeName) {
        Context ctx = new Context();
        ctx.setVariable("employeeName", employeeName);
        ctx.setVariable("companyName", COMPANY_NAME);

        String html = templateEngine.process("email/deactivation", ctx);
        sendHtml(toEmail, "Account Deactivated — " + COMPANY_NAME, html, "deactivation");
    }

    // =========================================================================
    // Import Summary Email
    // =========================================================================

    @Override
    @Async("taskExecutor")
    public void sendImportSummaryEmail(ImportResultDto result) {
        Context ctx = new Context();
        ctx.setVariable("totalRows",     result.getTotalRows());
        ctx.setVariable("successCount",  result.getSuccessCount());
        ctx.setVariable("failureCount",  result.getFailureCount());
        ctx.setVariable("errors",        result.getErrors());
        ctx.setVariable("companyName",   COMPANY_NAME);

        String html = templateEngine.process("email/import-summary", ctx);
        sendHtml(fromAddress, "Excel Import Completed — " + COMPANY_NAME, html, "import-summary");
    }

    // =========================================================================
    // Report Email (with attachment)
    // =========================================================================

    @Override
    @Async("taskExecutor")
    public void sendReportByEmail(String toEmail, String format, byte[] attachment, int totalRecords) {
        String formatLabel   = "excel".equalsIgnoreCase(format) ? "Excel Spreadsheet" : "PDF Document";
        String attachName    = "excel".equalsIgnoreCase(format)
                ? "employee_report.xlsx"
                : "employee_report.pdf";
        String mimeType      = "excel".equalsIgnoreCase(format)
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";

        String generatedAt = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"));

        Context ctx = new Context();
        ctx.setVariable("recipientEmail", toEmail);
        ctx.setVariable("format",         format.toLowerCase());
        ctx.setVariable("formatLabel",    formatLabel);
        ctx.setVariable("totalRecords",   totalRecords);
        ctx.setVariable("generatedAt",    generatedAt);
        ctx.setVariable("companyName",    COMPANY_NAME);

        String html    = templateEngine.process("email/report", ctx);
        String subject = "Your Employee Report (" + formatLabel + ") is Ready — " + COMPANY_NAME;

        sendHtmlWithAttachment(toEmail, subject, html, attachName, attachment, mimeType, "report");
    }

    // =========================================================================
    // Private — HTML send (no attachment)
    // =========================================================================

    private void sendHtml(String to, String subject, String htmlBody, String emailType) {
        if (!emailEnabled) {
            log.info("[EMAIL-MOCK] type={} | to={} | subject={}", emailType, to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[EMAIL-SENT] type={} | to={} | subject={}", emailType, to, subject);

        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("[EMAIL-FAILED] type={} | to={} | error: {}", emailType, to, e.getMessage());
        }
    }

    // =========================================================================
    // Private — HTML send with attachment
    // =========================================================================

    private void sendHtmlWithAttachment(String to, String subject, String htmlBody,
                                        String attachmentFilename, byte[] attachmentBytes,
                                        String attachmentMimeType, String emailType) {
        if (!emailEnabled) {
            log.info("[EMAIL-MOCK] type={} | to={} | subject={} | attachment={}",
                    emailType, to, subject, attachmentFilename);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addAttachment(attachmentFilename,
                    () -> new java.io.ByteArrayInputStream(attachmentBytes),
                    attachmentMimeType);

            mailSender.send(message);
            log.info("[EMAIL-SENT] type={} | to={} | attachment={} ({} bytes)",
                    emailType, to, attachmentFilename, attachmentBytes.length);

        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("[EMAIL-FAILED] type={} | to={} | error: {}", emailType, to, e.getMessage());
        }
    }
}
