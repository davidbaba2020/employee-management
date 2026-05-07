package com.assessment.employee.exception;

/**
 * Thrown when an attempt is made to create or update an employee with
 * an email address already registered to a different record.
 * Maps to HTTP {@code 409 Conflict} via {@link GlobalExceptionHandler}.
 */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("Email address '" + email + "' is already registered to another employee");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
