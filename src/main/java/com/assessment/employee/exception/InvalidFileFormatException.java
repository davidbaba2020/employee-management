package com.assessment.employee.exception;

/**
 * Thrown when the uploaded file has an unsupported format or extension.
 * Maps to HTTP {@code 400 Bad Request} via {@link GlobalExceptionHandler}.
 */
public class InvalidFileFormatException extends RuntimeException {

    public InvalidFileFormatException(String message) {
        super(message);
    }
}
