package com.assessment.employee.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Generic, immutable API response envelope used by every endpoint.
 *
 * <p>Implemented as a record so all fields are final by construction.
 * {@code @JsonInclude(NON_NULL)} ensures the {@code data} component is omitted
 * from the JSON output when it is {@code null} (e.g. 204 / error responses).
 *
 * <pre>
 * // Success:
 * { "success": true,  "status": 200, "message": "...", "data": {...}, "timestamp": "..." }
 *
 * // Error:
 * { "success": false, "status": 404, "message": "Employee not found", "timestamp": "..." }
 * </pre>
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String message,
        T data,
        LocalDateTime timestamp
) {

    // -------------------------------------------------------------------------
    // Static factory methods — callers never call the canonical constructor
    // directly because the timestamp must always be "now".
    // -------------------------------------------------------------------------

    /** 200 OK with a data payload. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, 200, message, data, LocalDateTime.now());
    }

    /** 201 Created with a data payload. */
    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, 201, message, data, LocalDateTime.now());
    }

    /** Any 2xx success with a data payload. */
    public static <T> ApiResponse<T> success(String message, T data, int status) {
        return new ApiResponse<>(true, status, message, data, LocalDateTime.now());
    }

    /** Any 2xx success with no data payload (e.g. 202, 204). */
    public static <T> ApiResponse<T> success(String message, int status) {
        return new ApiResponse<>(true, status, message, null, LocalDateTime.now());
    }

    /** Error with no details payload. */
    public static <T> ApiResponse<T> error(String message, int status) {
        return new ApiResponse<>(false, status, message, null, LocalDateTime.now());
    }

    /** Error with an additional details payload (e.g. field-level validation map). */
    public static <T> ApiResponse<T> error(String message, int status, T errorDetails) {
        return new ApiResponse<>(false, status, message, errorDetails, LocalDateTime.now());
    }
}
