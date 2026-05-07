package com.assessment.employee.exception;

import com.assessment.employee.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Centralised exception handler for the entire API.
 *
 * <p>Translates application and framework exceptions into structured
 * {@link ApiResponse} JSON objects with appropriate HTTP status codes.
 * Business logic is never leaking raw stack traces to clients.
 *
 * <p><strong>Status mapping:</strong>
 * <ul>
 *   <li>{@link EmployeeNotFoundException}       → 404 Not Found</li>
 *   <li>{@link DuplicateEmailException}         → 409 Conflict</li>
 *   <li>{@link InvalidFileFormatException}      → 400 Bad Request</li>
 *   <li>{@link ExcelProcessingException}        → 500 Internal Server Error</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 with field-level messages</li>
 *   <li>{@link ConstraintViolationException}    → 400 with field-level messages</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Custom Business Exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmployeeNotFound(EmployeeNotFoundException ex) {
        log.warn("Employee not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
        log.warn("Duplicate email detected: {}", ex.getEmail());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT.value()));
    }

    @ExceptionHandler(InvalidFileFormatException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileFormat(InvalidFileFormatException ex) {
        log.warn("Invalid file format: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(ExcelProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> handleExcelProcessing(ExcelProcessingException ex) {
        log.error("Excel processing error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Excel processing failed: " + ex.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    // -------------------------------------------------------------------------
    // Validation Exceptions
    // -------------------------------------------------------------------------

    /**
     * Handles {@code @Valid} failures on {@code @RequestBody} parameters.
     * Returns a map of field names to error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
            fieldErrors.put(fieldName, error.getDefaultMessage());
        });

        log.warn("Validation failed for request: {} field error(s)", fieldErrors.size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", HttpStatus.BAD_REQUEST.value(), fieldErrors));
    }

    /**
     * Handles {@code @Validated} + {@code @Valid} failures on service method parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        cv -> extractFieldName(cv),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing
                ));

        log.warn("Constraint violation: {} error(s)", fieldErrors.size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Constraint violation", HttpStatus.BAD_REQUEST.value(), fieldErrors));
    }

    // -------------------------------------------------------------------------
    // Infrastructure / Generic Exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Parameter '%s' has an invalid value: '%s'".formatted(ex.getName(), ex.getValue());
        log.warn("Type mismatch: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File upload too large: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Uploaded file exceeds the maximum allowed size of 10MB",
                        HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    /** Catch-all — never expose internal details to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected internal error occurred. Please try again later.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private String extractFieldName(ConstraintViolation<?> cv) {
        String propertyPath = cv.getPropertyPath().toString();
        // Strip method name prefix if present (e.g. "createEmployee.dto.firstName" → "firstName")
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
