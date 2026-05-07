package com.assessment.employee.service;

import com.assessment.employee.dto.response.ImportResultDto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for Excel import and export operations.
 * Implementations use Apache POI 5.x (XSSFWorkbook — xlsx format only).
 */
public interface ExcelService {

    /**
     * Parses an {@code .xlsx} file and imports the rows as Employee records.
     *
     * @param file the uploaded multipart Excel file
     * @return import result with success/failure counts and row-level error messages
     */
    ImportResultDto importFromExcel(MultipartFile file);

    /**
     * Writes all matching employees to the HTTP response as an {@code .xlsx} download.
     *
     * @param response   the servlet response to stream the workbook to
     * @param department optional department filter (null = all departments)
     * @param active     optional active filter (null = all statuses)
     */
    void exportToExcel(HttpServletResponse response, String department, Boolean active);

    /**
     * Generates the same Excel export and returns it as a byte array (for email attachments).
     *
     * @param department optional department filter (null = all departments)
     * @param active     optional active filter (null = all statuses)
     * @return the xlsx bytes
     */
    byte[] exportToExcelAsBytes(String department, Boolean active);
}
