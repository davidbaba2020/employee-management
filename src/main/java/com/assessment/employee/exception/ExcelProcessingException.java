package com.assessment.employee.exception;

/**
 * Thrown when an unexpected system error occurs during Excel file processing
 * (e.g. the file is corrupt or the I/O stream fails).
 * Maps to HTTP {@code 500 Internal Server Error} via {@link GlobalExceptionHandler}.
 */
public class ExcelProcessingException extends RuntimeException {

    public ExcelProcessingException(String message) {
        super(message);
    }

    public ExcelProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
