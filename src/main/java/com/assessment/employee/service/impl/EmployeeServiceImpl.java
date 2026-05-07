package com.assessment.employee.service.impl;

import com.assessment.employee.dto.request.EmployeePatchDto;
import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.EmployeeResponseDto;
import com.assessment.employee.dto.response.ImportResultDto;
import com.assessment.employee.dto.response.PagedResponse;
import com.assessment.employee.entity.Employee;
import com.assessment.employee.exception.DuplicateEmailException;
import com.assessment.employee.exception.EmployeeNotFoundException;
import com.assessment.employee.repository.EmployeeRepository;
import com.assessment.employee.service.EmailService;
import com.assessment.employee.service.EmployeeService;
import com.assessment.employee.service.ExcelService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);
    private static final String INTERN_DEPT = "Intern";

    @Value("${app.business.salary.floor.default:30000}")
    private BigDecimal salaryFloorDefault;

    @Value("${app.business.salary.floor.intern:15000}")
    private BigDecimal salaryFloorIntern;

    private final EmployeeRepository employeeRepository;
    private final ExcelService excelService;
    private final EmailService emailService;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               ExcelService excelService,
                               EmailService emailService) {
        this.employeeRepository = employeeRepository;
        this.excelService = excelService;
        this.emailService = emailService;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Override
    @Transactional
    public EmployeeResponseDto createEmployee(EmployeeRequestDto dto) {
        log.info("Creating employee with email: {}", dto.email());

        if (employeeRepository.existsByEmail(dto.email())) {
            log.warn("Duplicate email on create: {}", dto.email());
            throw new DuplicateEmailException(dto.email());
        }

        validateSalaryFloor(dto.department(), dto.salary());

        Employee saved = employeeRepository.save(toEntity(dto));
        log.info("Employee created: id={}, email={}", saved.getId(), saved.getEmail());

        emailService.sendWelcomeEmail(saved.getEmail(),
                saved.getFirstName() + " " + saved.getLastName());

        return toResponseDto(saved);
    }

    // =========================================================================
    // READ
    // =========================================================================

    @Override
    public PagedResponse<EmployeeResponseDto> getAllEmployees(String department,
                                                              Boolean active,
                                                              Pageable pageable) {
        log.debug("List employees — dept={}, active={}, page={}", department, active,
                pageable.getPageNumber());

        Page<Employee> page;
        if (department != null && active != null) {
            page = employeeRepository.findByDepartmentAndActive(department, active, pageable);
        } else if (department != null) {
            page = employeeRepository.findByDepartment(department, pageable);
        } else if (active != null) {
            page = employeeRepository.findByActive(active, pageable);
        } else {
            page = employeeRepository.findAll(pageable);
        }

        List<EmployeeResponseDto> dtos = page.getContent().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());

        return PagedResponse.of(dtos, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public EmployeeResponseDto getEmployeeById(Long id) {
        return toResponseDto(findOrThrow(id));
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @Override
    @Transactional
    public EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto dto) {
        log.info("Updating employee id={}", id);
        Employee existing = findOrThrow(id);

        if (employeeRepository.existsByEmailAndIdNot(dto.email(), id)) {
            throw new DuplicateEmailException(dto.email());
        }
        validateSalaryFloor(dto.department(), dto.salary());

        existing.setFirstName(dto.firstName());
        existing.setLastName(dto.lastName());
        existing.setEmail(dto.email());
        existing.setDepartment(dto.department());
        existing.setSalary(dto.salary());
        existing.setDateOfJoining(dto.dateOfJoining());
        existing.setActive(dto.active() != null ? dto.active() : existing.getActive());

        return toResponseDto(employeeRepository.save(existing));
    }

    @Override
    @Transactional
    public EmployeeResponseDto patchEmployee(Long id, EmployeePatchDto patch) {
        log.info("Patching employee id={}", id);
        Employee existing = findOrThrow(id);

        if (patch.salary() != null) {
            validateSalaryFloor(existing.getDepartment(), patch.salary());
            existing.setSalary(patch.salary());
        }
        if (patch.department() != null) existing.setDepartment(patch.department());
        if (patch.active()     != null) existing.setActive(patch.active());

        return toResponseDto(employeeRepository.save(existing));
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Override
    @Transactional
    public void softDeleteEmployee(Long id) {
        log.info("Soft-deleting employee id={}", id);
        Employee emp = findOrThrow(id);
        emp.setActive(false);
        employeeRepository.save(emp);

        emailService.sendDeactivationEmail(emp.getEmail(),
                emp.getFirstName() + " " + emp.getLastName());
    }

    @Override
    @Transactional
    public void hardDeleteEmployee(Long id) {
        log.info("Hard-deleting employee id={}", id);
        Employee emp = findOrThrow(id);

        if (Boolean.TRUE.equals(emp.getActive())) {
            throw new IllegalStateException(
                    "Cannot hard-delete an active employee. Deactivate first (DELETE /api/v1/employees/" + id + ")");
        }
        employeeRepository.delete(emp);
    }

    // =========================================================================
    // SALARY RANGE
    // =========================================================================

    @Override
    public List<EmployeeResponseDto> getEmployeesBySalaryRange(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min salary cannot be greater than max salary");
        }
        return employeeRepository.findBySalaryRange(min, max).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // EXCEL IMPORT
    // =========================================================================

    @Override
    @Transactional
    public ImportResultDto importEmployeesFromExcel(MultipartFile file) {
        log.info("Excel import: {}", file.getOriginalFilename());
        ImportResultDto result = excelService.importFromExcel(file);
        log.info("Import done — success={}, failures={}",
                result.getSuccessCount(), result.getFailureCount());
        emailService.sendImportSummaryEmail(result);
        return result;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Employee findOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private void validateSalaryFloor(String department, BigDecimal salary) {
        BigDecimal floor = INTERN_DEPT.equalsIgnoreCase(department)
                ? salaryFloorIntern : salaryFloorDefault;
        if (salary.compareTo(floor) < 0) {
            throw new IllegalArgumentException(
                    "Salary for '%s' must be ≥ %s (provided: %s)".formatted(department, floor, salary));
        }
    }

    /** Maps a request record to a new Employee entity. */
    private Employee toEntity(EmployeeRequestDto dto) {
        return Employee.builder()
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .email(dto.email())
                .department(dto.department())
                .salary(dto.salary())
                .dateOfJoining(dto.dateOfJoining())
                .active(dto.active())
                .build();
    }

    /** Maps an Employee entity to the response record. */
    public EmployeeResponseDto toResponseDto(Employee e) {
        return new EmployeeResponseDto(
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getDepartment(),
                e.getSalary(),
                e.getDateOfJoining(),
                e.getActive(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy()
        );
    }
}
