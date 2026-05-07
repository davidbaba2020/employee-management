package com.assessment.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing an employee record.
 *
 * <p>Contains only business fields.  Audit fields ({@code id}, {@code createdAt},
 * {@code updatedAt}, {@code createdBy}, {@code updatedBy}, {@code version}) are
 * inherited from {@link BaseEntity} and managed automatically by Spring Data JPA.
 *
 * <p>Salary floors are enforced at the service layer:
 * <ul>
 *   <li>Intern department: salary ≥ 15,000</li>
 *   <li>All other departments: salary ≥ 30,000</li>
 * </ul>
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "salary")
public class Employee extends BaseEntity {

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String department;

    @NotNull(message = "Salary is required")
    @DecimalMin(value = "0.00", message = "Salary must be a non-negative value")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal salary;

    @NotNull(message = "Date of joining is required")
    @PastOrPresent(message = "Date of joining must be today or in the past")
    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    /**
     * Soft-delete flag.  {@code true} = active employee; {@code false} = deactivated.
     * Hard-delete is only permitted when this is {@code false}.
     */
    @NotNull(message = "Active status is required")
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
