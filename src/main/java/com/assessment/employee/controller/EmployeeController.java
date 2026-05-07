package com.assessment.employee.controller;

import com.assessment.employee.dto.request.EmployeePatchDto;
import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.ApiResponse;
import com.assessment.employee.dto.response.EmployeeResponseDto;
import com.assessment.employee.dto.response.ImportResultDto;
import com.assessment.employee.dto.response.PagedResponse;
import com.assessment.employee.service.EmailService;
import com.assessment.employee.service.EmployeeService;
import com.assessment.employee.service.ExcelService;
import com.assessment.employee.service.PdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for Employee CRUD, import, export, and reporting endpoints.
 *
 * <p><strong>Design rules (enforced here):</strong>
 * <ul>
 *   <li>The controller is intentionally thin — zero business logic lives here.</li>
 *   <li>It delegates ALL logic to the service layer.</li>
 *   <li>It always wraps responses in {@link ApiResponse}.</li>
 *   <li>Entity classes are never exposed directly.</li>
 * </ul>
 *
 * <p>Base URL: {@code /api/v1/employees}
 */
@RestController
@RequestMapping("/api/v1/employees")
@Validated
@Tag(name = "Employees", description = "Employee management operations")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;
    private final ExcelService excelService;
    private final PdfService pdfService;
    private final EmailService emailService;

    public EmployeeController(EmployeeService employeeService,
                              ExcelService excelService,
                              PdfService pdfService,
                              EmailService emailService) {
        this.employeeService = employeeService;
        this.excelService = excelService;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    // =========================================================================
    // POST /api/v1/employees — Create
    // =========================================================================

    @PostMapping
    @Operation(summary = "Create a new employee",
               description = "Persists a new employee record. Email must be unique.")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> createEmployee(
            @Valid @RequestBody EmployeeRequestDto dto) {

        MDC.put("requestId", "CREATE-" + System.nanoTime());
        log.info("POST /api/v1/employees — email={}", dto.email());

        EmployeeResponseDto created = employeeService.createEmployee(dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Employee created successfully", created));
    }

    // =========================================================================
    // GET /api/v1/employees — List (paginated, filterable)
    // =========================================================================

    @GetMapping
    @Operation(summary = "List all employees (paginated)",
               description = "Returns a paginated list. Optional filters: department, active.")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeResponseDto>>> getAllEmployees(
            @Parameter(description = "Filter by department name (case-sensitive)")
            @RequestParam(required = false) String department,

            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean active,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "id") String sort,

            @Parameter(description = "Sort direction: asc or desc")
            @RequestParam(defaultValue = "asc") String direction) {

        log.debug("GET /api/v1/employees — dept={}, active={}, page={}, size={}", department, active, page, size);

        Sort.Direction sortDir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sort));

        PagedResponse<EmployeeResponseDto> result = employeeService.getAllEmployees(department, active, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Employees retrieved successfully", result));
    }

    // =========================================================================
    // GET /api/v1/employees/{id} — Get by ID
    // =========================================================================

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(
            @PathVariable Long id) {

        log.debug("GET /api/v1/employees/{}", id);
        EmployeeResponseDto dto = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(ApiResponse.ok("Employee retrieved successfully", dto));
    }

    // =========================================================================
    // PUT /api/v1/employees/{id} — Full update
    // =========================================================================

    @PutMapping("/{id}")
    @Operation(summary = "Fully update an employee (PUT)",
               description = "All fields in the request body replace the existing values.")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequestDto dto) {

        log.info("PUT /api/v1/employees/{}", id);
        EmployeeResponseDto updated = employeeService.updateEmployee(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Employee updated successfully", updated));
    }

    // =========================================================================
    // PATCH /api/v1/employees/{id} — Partial update
    // =========================================================================

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update an employee (PATCH)",
               description = "Only salary, department and active fields can be patched.")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> patchEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeePatchDto patchDto) {

        log.info("PATCH /api/v1/employees/{}", id);
        EmployeeResponseDto patched = employeeService.patchEmployee(id, patchDto);
        return ResponseEntity.ok(ApiResponse.ok("Employee patched successfully", patched));
    }

    // =========================================================================
    // DELETE /api/v1/employees/{id} — Soft delete
    // =========================================================================

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete an employee",
               description = "Sets active=false. The record remains in the database.")
    public ResponseEntity<ApiResponse<Void>> softDeleteEmployee(@PathVariable Long id) {
        log.info("DELETE (soft) /api/v1/employees/{}", id);
        employeeService.softDeleteEmployee(id);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Employee deactivated successfully", HttpStatus.NO_CONTENT.value()));
    }

    // =========================================================================
    // DELETE /api/v1/employees/{id}/hard — Hard delete
    // =========================================================================

    @DeleteMapping("/{id}/hard")
    @Operation(summary = "Hard-delete an inactive employee",
               description = "Permanently removes the record. Only allowed when active=false.")
    public ResponseEntity<ApiResponse<Void>> hardDeleteEmployee(@PathVariable Long id) {
        log.info("DELETE (hard) /api/v1/employees/{}", id);
        employeeService.hardDeleteEmployee(id);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Employee permanently deleted", HttpStatus.NO_CONTENT.value()));
    }

    // =========================================================================
    // GET /api/v1/employees/salary-range — Filter by salary
    // =========================================================================

    @GetMapping("/salary-range")
    @Operation(summary = "Find employees by salary range",
               description = "Returns employees whose salary is between min and max (inclusive).")
    public ResponseEntity<ApiResponse<List<EmployeeResponseDto>>> getEmployeesBySalaryRange(
            @Parameter(description = "Minimum salary (inclusive)", required = true)
            @RequestParam @DecimalMin("0.00") BigDecimal min,

            @Parameter(description = "Maximum salary (inclusive)", required = true)
            @RequestParam @DecimalMin("0.00") BigDecimal max) {

        log.debug("GET /api/v1/employees/salary-range — min={}, max={}", min, max);
        List<EmployeeResponseDto> employees = employeeService.getEmployeesBySalaryRange(min, max);
        return ResponseEntity.ok(ApiResponse.ok(
                "Found " + employees.size() + " employee(s) in salary range", employees));
    }

    // =========================================================================
    // POST /api/v1/employees/import — Excel import
    // =========================================================================

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import employees from an .xlsx file",
               description = "Upload an Excel file. Row-level errors are reported without aborting the import.")
    public ResponseEntity<ApiResponse<ImportResultDto>> importEmployees(
            @Parameter(description = "Excel .xlsx file (field name: file)")
            @RequestParam("file") MultipartFile file) {

        log.info("POST /api/v1/employees/import — filename={}, size={}", file.getOriginalFilename(), file.getSize());
        ImportResultDto result = employeeService.importEmployeesFromExcel(file);

        HttpStatus status = result.getFailureCount() > 0 ? HttpStatus.MULTI_STATUS : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("Import completed", result, status.value()));
    }

    // =========================================================================
    // GET /api/v1/employees/export/excel — Excel export
    // =========================================================================

    @GetMapping("/export/excel")
    @Operation(summary = "Export employees to Excel (.xlsx)",
               description = "Downloads an Excel file. Optional filters: department, active.")
    public void exportToExcel(
            HttpServletResponse response,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean active) {

        log.info("GET /api/v1/employees/export/excel — dept={}, active={}", department, active);
        excelService.exportToExcel(response, department, active);
    }

    // =========================================================================
    // GET /api/v1/employees/export/pdf — PDF report
    // =========================================================================

    @GetMapping("/export/pdf")
    @Operation(summary = "Export employees to PDF",
               description = "Downloads a formatted PDF report of all employees.")
    public void exportToPdf(HttpServletResponse response) {
        log.info("GET /api/v1/employees/export/pdf");
        pdfService.generateEmployeePdfReport(response);
    }

    // =========================================================================
    // POST /api/v1/employees/export/email — Email report as PDF or Excel
    // =========================================================================

    @PostMapping("/export/email")
    @Operation(summary = "Email employee report as PDF or Excel",
               description = "Generates the full employee report in the requested format and sends "
                           + "it as an email attachment. Returns 202 immediately; delivery is async.")
    public ResponseEntity<ApiResponse<Void>> emailReport(
            @Parameter(description = "Recipient email address", required = true)
            @RequestParam @Email String email,

            @Parameter(description = "Report format: pdf or excel", required = true)
            @RequestParam @Pattern(regexp = "^(pdf|excel)$", message = "format must be 'pdf' or 'excel'")
            String format) {

        log.info("POST /api/v1/employees/export/email — to={}, format={}", email, format);

        int totalRecords = (int) employeeService
                .getAllEmployees(null, null,
                        org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
                .totalElements();

        byte[] attachment = "excel".equalsIgnoreCase(format)
                ? excelService.exportToExcelAsBytes(null, null)
                : pdfService.generateEmployeePdfReportAsBytes();

        emailService.sendReportByEmail(email, format, attachment, totalRecords);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "Report is being sent to " + email, HttpStatus.ACCEPTED.value()));
    }
}
