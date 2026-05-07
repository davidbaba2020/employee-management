package com.assessment.employee.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service interface for PDF report generation.
 * Implementation uses OpenPDF (a fork of iText 4).
 */
public interface PdfService {

    /**
     * Generates an employee report PDF and writes it directly to the HTTP response output stream.
     *
     * @param response the servlet response to stream the PDF to
     */
    void generateEmployeePdfReport(HttpServletResponse response);

    /**
     * Generates the same PDF report and returns it as a byte array (for email attachments).
     *
     * @return the PDF bytes
     */
    byte[] generateEmployeePdfReportAsBytes();
}
