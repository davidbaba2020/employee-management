package com.assessment.employee.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable request payload for creating or fully replacing an employee (POST / PUT).
 *
 * <p>Using a Java record enforces immutability at the language level — the data
 * arriving from the client cannot be mutated after deserialization.  Validation
 * annotations on record components are propagated to both the backing field and
 * the canonical constructor parameter, so {@code @Valid} works identically to a
 * regular bean DTO.
 *
 * <p>If {@code active} is omitted from the JSON body it defaults to {@code true}
 * via the compact constructor.
 */
public record EmployeeRequestDto(

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Department is required")
        @Size(max = 100, message = "Department must not exceed 100 characters")
        String department,

        @NotNull(message = "Salary is required")
        @DecimalMin(value = "0.00", message = "Salary must be a non-negative value")
        BigDecimal salary,

        @NotNull(message = "Date of joining is required")
        @PastOrPresent(message = "Date of joining must be today or in the past")
        LocalDate dateOfJoining,

        Boolean active

) {
    /**
     * Compact constructor — defaults {@code active} to {@code true} when absent.
     */
    public EmployeeRequestDto {
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}
