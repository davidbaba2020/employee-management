package com.assessment.employee.service;

import com.assessment.employee.dto.request.EmployeePatchDto;
import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.EmployeeResponseDto;
import com.assessment.employee.dto.response.ImportResultDto;
import com.assessment.employee.dto.response.PagedResponse;
import com.assessment.employee.exception.DuplicateEmailException;
import com.assessment.employee.exception.EmployeeNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for Employee business operations.
 *
 * <p>All business rules are enforced here — the controller layer must remain
 * thin and delegation-only. JavaDoc on each method describes the exact contract,
 * including what exceptions are thrown and when.
 */
public interface EmployeeService {

    // =========================================================================
    // CRUD
    // =========================================================================

    /**
     * Creates a new employee record.
     *
     * <p>Business rules enforced:
     * <ol>
     *   <li>Email must not already exist in the database.</li>
     *   <li>Salary must meet the department floor (Intern ≥ 15,000; others ≥ 30,000).</li>
     * </ol>
     *
     * @param dto validated request payload
     * @return the persisted employee as a response DTO
     * @throws DuplicateEmailException if the email is already taken
     * @throws IllegalArgumentException if the salary is below the department floor
     */
    EmployeeResponseDto createEmployee(EmployeeRequestDto dto);

    /**
     * Returns a paginated, optionally filtered list of employees.
     *
     * @param department optional department filter (null = no filter)
     * @param active     optional active-status filter (null = no filter)
     * @param pageable   pagination and sorting parameters
     * @return page of employee DTOs
     */
    PagedResponse<EmployeeResponseDto> getAllEmployees(String department, Boolean active, Pageable pageable);

    /**
     * Retrieves a single employee by primary key.
     *
     * @param id the employee ID
     * @return the matching employee DTO
     * @throws EmployeeNotFoundException if no record exists with the given ID
     */
    EmployeeResponseDto getEmployeeById(Long id);

    /**
     * Fully replaces an existing employee record (PUT semantics).
     * All fields from the DTO overwrite the current values.
     *
     * @param id  the employee ID to update
     * @param dto validated replacement payload
     * @return the updated employee DTO
     * @throws EmployeeNotFoundException if no record exists with the given ID
     * @throws DuplicateEmailException   if the new email conflicts with another record
     */
    EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto dto);

    /**
     * Partially updates an employee (PATCH semantics).
     * Only non-null fields in the patch DTO are applied.
     * Allowed fields: {@code salary}, {@code department}, {@code active}.
     *
     * @param id       the employee ID to patch
     * @param patchDto fields to update (non-null fields only)
     * @return the updated employee DTO
     * @throws EmployeeNotFoundException if no record exists with the given ID
     */
    EmployeeResponseDto patchEmployee(Long id, EmployeePatchDto patchDto);

    /**
     * Soft-deletes an employee by setting {@code active = false}.
     * The record is retained in the database for audit purposes.
     *
     * @param id the employee ID to deactivate
     * @throws EmployeeNotFoundException if no record exists with the given ID
     */
    void softDeleteEmployee(Long id);

    /**
     * Hard-deletes an employee from the database.
     * Only allowed when the employee is already inactive ({@code active = false}).
     *
     * @param id the employee ID to remove permanently
     * @throws EmployeeNotFoundException if no record exists with the given ID
     * @throws IllegalStateException     if the employee is still active
     */
    void hardDeleteEmployee(Long id);

    // =========================================================================
    // FILTERING
    // =========================================================================

    /**
     * Finds employees whose salary falls within the inclusive range.
     *
     * @param min lower bound (inclusive)
     * @param max upper bound (inclusive)
     * @return list of matching employee DTOs
     */
    List<EmployeeResponseDto> getEmployeesBySalaryRange(BigDecimal min, BigDecimal max);

    // =========================================================================
    // IMPORT
    // =========================================================================

    /**
     * Bulk-imports employees from an {@code .xlsx} file.
     *
     * <p>Processing rules:
     * <ul>
     *   <li>Only {@code .xlsx} format is accepted; {@code .xls} is rejected.</li>
     *   <li>Row 1 is the header and is skipped.</li>
     *   <li>Each data row is mapped to a DTO and validated independently.</li>
     *   <li>Validation failures are recorded in the result; they do not abort the import.</li>
     *   <li>System errors cause a full rollback.</li>
     * </ul>
     *
     * @param file the uploaded {@code .xlsx} file
     * @return import result with success/failure counts and per-row error messages
     */
    ImportResultDto importEmployeesFromExcel(MultipartFile file);
}
