package com.assessment.employee.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable read model returned by every employee endpoint.
 *
 * <p>The entity is never exposed directly; this record presents only what
 * clients need. Audit fields ({@code createdBy}, {@code updatedBy}) are
 * included so consumers can see who last touched a record without querying
 * a separate audit log.
 *
 * <p>Record accessor names follow Java convention (no {@code get} prefix).
 * Jackson 2.12+ serialises records using component names directly, so the
 * JSON keys are identical to the component names: {@code firstName},
 * {@code lastName}, etc.
 */
public record EmployeeResponseDto(

        Long id,
        String firstName,
        String lastName,
        String email,
        String department,
        BigDecimal salary,
        LocalDate dateOfJoining,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy

) {
    /** Convenience accessor — full name derived from components. */
    public String fullName() {
        return firstName + " " + lastName;
    }
}
