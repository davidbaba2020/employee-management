package com.assessment.employee.controller;

import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.EmployeeResponseDto;
import com.assessment.employee.dto.response.PagedResponse;
import com.assessment.employee.exception.DuplicateEmailException;
import com.assessment.employee.exception.EmployeeNotFoundException;
import com.assessment.employee.exception.GlobalExceptionHandler;
import com.assessment.employee.service.EmailService;
import com.assessment.employee.service.EmployeeService;
import com.assessment.employee.service.ExcelService;
import com.assessment.employee.service.PdfService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest slice for {@link EmployeeController}.
 *
 * <p>Uses MockMvc to test the HTTP layer without starting a full server.
 * Service dependencies are mocked with Mockito.
 */
@WebMvcTest(EmployeeController.class)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private ExcelService excelService;

    @MockBean
    private PdfService pdfService;

    @MockBean
    private EmailService emailService;

    private ObjectMapper objectMapper;
    private EmployeeResponseDto sampleResponse;
    private EmployeeRequestDto sampleRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleResponse = new EmployeeResponseDto(
                1L, "Jane", "Doe", "jane.doe@example.com",
                "Engineering", new BigDecimal("75000.00"),
                LocalDate.of(2023, 6, 1), true,
                LocalDateTime.now(), LocalDateTime.now(),
                "system", "system");

        sampleRequest = new EmployeeRequestDto(
                "Jane", "Doe", "jane.doe@example.com",
                "Engineering", new BigDecimal("75000.00"),
                LocalDate.of(2023, 6, 1), true);
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/employees")
    class CreateEmployee {

        @Test
        @DisplayName("Should return 201 when employee is created successfully")
        void shouldReturn201OnSuccess() throws Exception {
            when(employeeService.createEmployee(any(EmployeeRequestDto.class)))
                    .thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/employees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"));
        }

        @Test
        @DisplayName("Should return 400 when firstName is blank")
        void shouldReturn400WhenFirstNameBlank() throws Exception {
            EmployeeRequestDto invalidRequest = new EmployeeRequestDto(
                    "", "Doe", "jane.doe@example.com",
                    "Engineering", new BigDecimal("75000.00"),
                    LocalDate.of(2023, 6, 1), true);

            mockMvc.perform(post("/api/v1/employees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.firstName").exists());
        }

        @Test
        @DisplayName("Should return 409 when email is duplicate")
        void shouldReturn409OnDuplicateEmail() throws Exception {
            when(employeeService.createEmployee(any(EmployeeRequestDto.class)))
                    .thenThrow(new DuplicateEmailException("jane.doe@example.com"));

            mockMvc.perform(post("/api/v1/employees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // =========================================================================
    // READ
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/employees/{id}")
    class GetById {

        @Test
        @DisplayName("Should return 200 with employee when found")
        void shouldReturn200WhenFound() throws Exception {
            when(employeeService.getEmployeeById(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/employees/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("Should return 404 when employee not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(employeeService.getEmployeeById(99L))
                    .thenThrow(new EmployeeNotFoundException(99L));

            mockMvc.perform(get("/api/v1/employees/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Nested
    @DisplayName("DELETE /api/v1/employees/{id}")
    class SoftDelete {

        @Test
        @DisplayName("Should return 204 on soft delete")
        void shouldReturn204OnSoftDelete() throws Exception {
            doNothing().when(employeeService).softDeleteEmployee(1L);

            mockMvc.perform(delete("/api/v1/employees/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent employee")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new EmployeeNotFoundException(99L))
                    .when(employeeService).softDeleteEmployee(99L);

            mockMvc.perform(delete("/api/v1/employees/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // SALARY RANGE
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/employees/salary-range")
    class SalaryRange {

        @Test
        @DisplayName("Should return matching employees")
        void shouldReturnMatchingEmployees() throws Exception {
            when(employeeService.getEmployeesBySalaryRange(any(BigDecimal.class), any(BigDecimal.class)))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/v1/employees/salary-range")
                            .param("min", "50000")
                            .param("max", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].email").value("jane.doe@example.com"));
        }
    }
}
