package com.assessment.employee.repository;

import com.assessment.employee.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Employee} entity.
 *
 * <p>Spring automatically generates the implementation at runtime — no code is
 * needed in the body. The method names follow Spring Data's naming convention
 * (e.g. {@code findBy<Field>}) and the {@code @Query} annotation is used for
 * custom JPQL that cannot be expressed with method naming alone.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // -------------------------------------------------------------------------
    // Derived Query Methods (Spring Data naming convention)
    // -------------------------------------------------------------------------

    /** Returns all employees belonging to the given department (case-sensitive). */
    List<Employee> findByDepartment(String department);

    /** Looks up an employee by their unique email address. */
    Optional<Employee> findByEmail(String email);

    /** Returns only employees whose {@code active} flag is {@code true}. */
    List<Employee> findByActiveTrue();

    /**
     * Checks whether an email exists for any employee <em>other than</em>
     * the given ID. Used by the service to validate updates without false positives.
     *
     * @param email      the email to check
     * @param excludedId the employee whose record should be ignored
     * @return {@code true} if the email is taken by a different record
     */
    boolean existsByEmailAndIdNot(String email, Long excludedId);

    /** Checks if an email is already registered (used during create). */
    boolean existsByEmail(String email);

    // -------------------------------------------------------------------------
    // Paginated Queries (for the list endpoint)
    // -------------------------------------------------------------------------

    /** Paginated list filtered by department and active status. */
    Page<Employee> findByDepartmentAndActive(String department, Boolean active, Pageable pageable);

    /** Paginated list filtered by department only. */
    Page<Employee> findByDepartment(String department, Pageable pageable);

    /** Paginated list filtered by active status only. */
    Page<Employee> findByActive(Boolean active, Pageable pageable);

    // -------------------------------------------------------------------------
    // Custom JPQL Queries
    // -------------------------------------------------------------------------

    /**
     * Returns employees whose salary falls within the inclusive range [{@code min}, {@code max}].
     *
     * @param min lower bound (inclusive)
     * @param max upper bound (inclusive)
     * @return matching employees
     */
    @Query("SELECT e FROM Employee e WHERE e.salary BETWEEN :min AND :max")
    List<Employee> findBySalaryRange(
            @Param("min") BigDecimal min,
            @Param("max") BigDecimal max);

    /** Hard-deletes all employees whose {@code active} flag is {@code false}. */
    @Query("DELETE FROM Employee e WHERE e.active = false")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int purgeInactiveEmployees();
}
