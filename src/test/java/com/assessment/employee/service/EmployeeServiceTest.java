package com.assessment.employee.service;

import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.EmployeeResponseDto;
import com.assessment.employee.entity.Employee;
import com.assessment.employee.exception.DuplicateEmailException;
import com.assessment.employee.exception.EmployeeNotFoundException;
import com.assessment.employee.repository.EmployeeRepository;
import com.assessment.employee.service.impl.EmployeeServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmployeeServiceImpl} using Mockito.
 * The repository and email service are mocked — no database is used.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ExcelService excelService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @BeforeEach
    void injectConfigValues() {
        // Inject @Value fields that Spring would normally provide
        ReflectionTestUtils.setField(employeeService, "salaryFloorDefault", new BigDecimal("30000"));
        ReflectionTestUtils.setField(employeeService, "salaryFloorIntern", new BigDecimal("15000"));
    }

    private Employee buildEmployee(Long id) {
        Employee emp = Employee.builder()
                .firstName("John")
                .lastName("Smith")
                .email("john.smith@example.com")
                .department("Engineering")
                .salary(new BigDecimal("50000"))
                .dateOfJoining(LocalDate.of(2022, 1, 10))
                .active(true)
                .build();
        // id lives in BaseEntity — set via reflection since @Builder doesn't include it
        ReflectionTestUtils.setField(emp, "id", id);
        return emp;
    }

    private EmployeeRequestDto buildRequest() {
        return new EmployeeRequestDto(
                "John", "Smith", "john.smith@example.com",
                "Engineering", new BigDecimal("50000"),
                LocalDate.of(2022, 1, 10), true);
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Nested
    @DisplayName("createEmployee()")
    class CreateEmployee {

        @Test
        @DisplayName("Should save and return DTO when all rules pass")
        void shouldSaveEmployee() {
            EmployeeRequestDto request = buildRequest();
            Employee saved = buildEmployee(1L);

            when(employeeRepository.existsByEmail(request.email())).thenReturn(false);
            when(employeeRepository.save(any(Employee.class))).thenReturn(saved);

            EmployeeResponseDto result = employeeService.createEmployee(request);

            assertThat(result).isNotNull();
            assertThat(result.email()).isEqualTo("john.smith@example.com");
            verify(employeeRepository).save(any(Employee.class));
        }

        @Test
        @DisplayName("Should throw DuplicateEmailException when email already exists")
        void shouldThrowDuplicateEmail() {
            EmployeeRequestDto request = buildRequest();
            when(employeeRepository.existsByEmail(request.email())).thenReturn(true);

            assertThatThrownBy(() -> employeeService.createEmployee(request))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining("john.smith@example.com");

            verify(employeeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when Engineering salary below 30000")
        void shouldRejectLowSalaryForEngineering() {
            EmployeeRequestDto request = new EmployeeRequestDto(
                    "John", "Smith", "john.smith@example.com",
                    "Engineering", new BigDecimal("20000"),
                    LocalDate.of(2022, 1, 10), true);

            when(employeeRepository.existsByEmail(request.email())).thenReturn(false);

            assertThatThrownBy(() -> employeeService.createEmployee(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("30000");
        }

        @Test
        @DisplayName("Should accept Intern salary of 15000 (at floor)")
        void shouldAcceptInternSalaryAtFloor() {
            EmployeeRequestDto request = new EmployeeRequestDto(
                    "John", "Smith", "john.smith@example.com",
                    "Intern", new BigDecimal("15000"),
                    LocalDate.of(2022, 1, 10), true);

            Employee saved = buildEmployee(2L);
            saved.setDepartment("Intern");
            saved.setSalary(new BigDecimal("15000"));

            when(employeeRepository.existsByEmail(request.email())).thenReturn(false);
            when(employeeRepository.save(any(Employee.class))).thenReturn(saved);

            EmployeeResponseDto result = employeeService.createEmployee(request);
            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // GET BY ID
    // =========================================================================

    @Nested
    @DisplayName("getEmployeeById()")
    class GetById {

        @Test
        @DisplayName("Should return DTO when employee exists")
        void shouldReturnDto() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(buildEmployee(1L)));

            EmployeeResponseDto result = employeeService.getEmployeeById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.firstName()).isEqualTo("John");
        }

        @Test
        @DisplayName("Should throw EmployeeNotFoundException when not found")
        void shouldThrowNotFound() {
            when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // =========================================================================
    // SOFT DELETE
    // =========================================================================

    @Nested
    @DisplayName("softDeleteEmployee()")
    class SoftDelete {

        @Test
        @DisplayName("Should set active=false and save")
        void shouldSoftDelete() {
            Employee employee = buildEmployee(1L);
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
            when(employeeRepository.save(employee)).thenReturn(employee);

            employeeService.softDeleteEmployee(1L);

            assertThat(employee.getActive()).isFalse();
            verify(employeeRepository).save(employee);
        }
    }

    // =========================================================================
    // HARD DELETE
    // =========================================================================

    @Nested
    @DisplayName("hardDeleteEmployee()")
    class HardDelete {

        @Test
        @DisplayName("Should hard-delete when employee is inactive")
        void shouldHardDeleteInactive() {
            Employee employee = buildEmployee(1L);
            employee.setActive(false);

            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

            employeeService.hardDeleteEmployee(1L);

            verify(employeeRepository).delete(employee);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when employee is still active")
        void shouldThrowWhenActive() {
            Employee employee = buildEmployee(1L);
            employee.setActive(true);

            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

            assertThatThrownBy(() -> employeeService.hardDeleteEmployee(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active");
        }
    }
}
