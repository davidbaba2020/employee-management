package com.assessment.employee.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Immutable payload for partial employee updates (PATCH).
 *
 * <p>Only the three fields that the PATCH endpoint supports are present.
 * A {@code null} component means "leave this field unchanged"; Jackson will
 * deserialize absent JSON properties as {@code null} for reference types.
 *
 * <p>No {@code @NotNull} constraints here — all fields are intentionally optional.
 * Business rules (e.g. salary floor) are enforced in the service layer only when
 * the field is non-null.
 */
public record EmployeePatchDto(

        @DecimalMin(value = "0.00", message = "Salary must be a non-negative value")
        BigDecimal salary,

        @Size(max = 100, message = "Department must not exceed 100 characters")
        String department,

        Boolean active

) {}
