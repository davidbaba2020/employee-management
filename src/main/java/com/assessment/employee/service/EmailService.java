package com.assessment.employee.service;

import com.assessment.employee.dto.response.ImportResultDto;

/**
 * Service interface for email notification operations.
 *
 * <p>All methods are asynchronous — they use {@code @Async} under the hood
 * so that email sending never blocks or rolls back an HTTP request.
 *
 * <p>When {@code app.email.enabled=false} (default for dev), the implementation
 * logs the email content instead of actually sending it.
 */
public interface EmailService {

    void sendWelcomeEmail(String toEmail, String employeeName);

    void sendDeactivationEmail(String toEmail, String employeeName);

    void sendImportSummaryEmail(ImportResultDto result);

    /**
     * Generates the employee report in the requested format and emails it as an attachment.
     *
     * @param toEmail       recipient address
     * @param format        {@code "pdf"} or {@code "excel"}
     * @param attachment    the pre-generated file bytes
     * @param totalRecords  total number of records in the report (for the email body)
     */
    void sendReportByEmail(String toEmail, String format, byte[] attachment, int totalRecords);
}
