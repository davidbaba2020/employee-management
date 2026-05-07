package com.assessment.employee.exception;

/**
 * Thrown when an Employee record cannot be found by the given identifier.
 * Maps to HTTP {@code 404 Not Found} via {@link GlobalExceptionHandler}.
 */
public class EmployeeNotFoundException extends RuntimeException {

    private final Long employeeId;

    public EmployeeNotFoundException(Long id) {
        super("Employee with id " + id + " not found");
        this.employeeId = id;
    }

    public EmployeeNotFoundException(String message) {
        super(message);
        this.employeeId = null;
    }

    public Long getEmployeeId() {
        return employeeId;
    }
}
