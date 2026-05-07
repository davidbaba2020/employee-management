package com.assessment.employee.service.impl;

import com.assessment.employee.dto.request.EmployeeRequestDto;
import com.assessment.employee.dto.response.ImportResultDto;
import com.assessment.employee.entity.Employee;
import com.assessment.employee.exception.ExcelProcessingException;
import com.assessment.employee.exception.InvalidFileFormatException;
import com.assessment.employee.repository.EmployeeRepository;
import com.assessment.employee.service.ExcelService;
import com.assessment.employee.util.CellExtractorUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Apache POI–based implementation of {@link ExcelService}.
 *
 * <p>Export generates a two-sheet workbook:
 * <ul>
 *   <li><b>Sheet 1 "Employees"</b> — decorated table with department-coloured rows,
 *       status emoji, salary formatting, frozen header, auto-filter.</li>
 *   <li><b>Sheet 2 "Summary"</b> — key stats: headcount, active/inactive counts,
 *       average salary, top earner, per-department breakdown.</li>
 * </ul>
 */
@Service
public class ExcelServiceImpl implements ExcelService {

    private static final Logger log = LoggerFactory.getLogger(ExcelServiceImpl.class);

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String XLSX_EXTENSION = ".xlsx";

    // Import column indices
    private static final int COL_FIRST_NAME = 0;
    private static final int COL_LAST_NAME  = 1;
    private static final int COL_EMAIL      = 2;
    private static final int COL_DEPARTMENT = 3;
    private static final int COL_SALARY     = 4;
    private static final int COL_JOIN_DATE  = 5;
    private static final int COL_ACTIVE     = 6;

    // Export headers
    private static final String[] EXPORT_HEADERS = {
        "ID", "First Name", "Last Name", "Email",
        "Department", "Salary", "Date of Joining", "Status", "Created At"
    };

    // Department pastel colours (ARGB hex, no alpha byte needed — POI uses 0-padded RGB)
    private static final Map<String, byte[]> DEPT_COLORS = new LinkedHashMap<>();
    static {
        DEPT_COLORS.put("Engineering", rgb(189, 215, 238));
        DEPT_COLORS.put("HR",          rgb(198, 224, 180));
        DEPT_COLORS.put("Finance",     rgb(255, 242, 204));
        DEPT_COLORS.put("Marketing",   rgb(231, 215, 244));
        DEPT_COLORS.put("Sales",       rgb(252, 228, 214));
        DEPT_COLORS.put("Operations",  rgb(208, 236, 244));
        DEPT_COLORS.put("Intern",      rgb(252, 228, 236));
    }
    private static final byte[] DEFAULT_ROW_COLOR = rgb(245, 245, 245);

    private final EmployeeRepository employeeRepository;
    private final Validator validator;

    public ExcelServiceImpl(EmployeeRepository employeeRepository, Validator validator) {
        this.employeeRepository = employeeRepository;
        this.validator = validator;
    }

    // =========================================================================
    // IMPORT
    // =========================================================================

    @Override
    @Transactional
    public ImportResultDto importFromExcel(MultipartFile file) {
        validateFileExtension(file);
        log.info("Processing Excel import: file={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        ImportResultDto result = new ImportResultDto();

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                XSSFRow row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row)) continue;
                processRow(row, rowIdx + 1, result);
            }
        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage(), e);
            throw new ExcelProcessingException("Failed to read the uploaded Excel file: " + e.getMessage(), e);
        }

        log.info("Excel import finished — success={}, failures={}",
                result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    // =========================================================================
    // EXPORT — HTTP stream
    // =========================================================================

    @Override
    public void exportToExcel(HttpServletResponse response, String department, Boolean active) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename  = "employees_" + timestamp + XLSX_EXTENSION;

        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        List<Employee> employees = fetchEmployeesForExport(department, active);
        log.info("Exporting {} employees to Excel (HTTP stream)", employees.size());

        try {
            buildWorkbook(employees, response.getOutputStream());
        } catch (IOException e) {
            log.error("Excel export stream error: {}", e.getMessage(), e);
            throw new ExcelProcessingException("Failed to write Excel export", e);
        }
    }

    // =========================================================================
    // EXPORT — byte array (for email attachment)
    // =========================================================================

    @Override
    public byte[] exportToExcelAsBytes(String department, Boolean active) {
        List<Employee> employees = fetchEmployeesForExport(department, active);
        log.info("Generating Excel for {} employees (byte array)", employees.size());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildWorkbook(employees, baos);
        return baos.toByteArray();
    }

    // =========================================================================
    // Core workbook builder
    // =========================================================================

    private void buildWorkbook(List<Employee> employees, OutputStream out) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            buildEmployeeSheet(wb, employees);
            buildSummarySheet(wb, employees);

            wb.write(out);
            out.flush();
        } catch (IOException e) {
            log.error("Workbook build error: {}", e.getMessage(), e);
            throw new ExcelProcessingException("Failed to generate Excel workbook", e);
        }
    }

    // =========================================================================
    // Sheet 1 — Employees
    // =========================================================================

    private void buildEmployeeSheet(XSSFWorkbook wb, List<Employee> employees) {
        XSSFSheet sheet = wb.createSheet("👥 Employees");

        // Pre-build styles
        XSSFCellStyle titleStyle   = createTitleStyle(wb);
        XSSFCellStyle subTitleStyle= createSubTitleStyle(wb);
        XSSFCellStyle headerStyle  = createHeaderStyle(wb);
        XSSFCellStyle salaryStyle  = createSalaryStyle(wb, false);
        XSSFCellStyle salaryStyleI = createSalaryStyle(wb, true);

        // Title block (rows 0-1, merged across all columns)
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, EXPORT_HEADERS.length - 1));
        XSSFRow titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(36);
        XSSFCell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("🏢  Employee Directory Report");
        titleCell.setCellStyle(titleStyle);

        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, EXPORT_HEADERS.length - 1));
        XSSFRow subRow = sheet.createRow(1);
        subRow.setHeightInPoints(20);
        XSSFCell subCell = subRow.createCell(0);
        String meta = "Generated: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))
                + "   ·   " + employees.size() + " records";
        subCell.setCellValue(meta);
        subCell.setCellStyle(subTitleStyle);

        // Empty separator row
        sheet.createRow(2).setHeightInPoints(8);

        // Header row (row 3)
        XSSFRow headerRow = sheet.createRow(3);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            XSSFCell cell = headerRow.createCell(i);
            cell.setCellValue(EXPORT_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows start at row 4
        DateTimeFormatter dtf    = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter dtfTs  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (int i = 0; i < employees.size(); i++) {
            Employee emp = employees.get(i);
            boolean inactive = Boolean.FALSE.equals(emp.getActive());
            XSSFCellStyle rowStyle   = createDataStyle(wb, emp.getDepartment(), inactive, false);
            XSSFCellStyle salStyle   = inactive ? salaryStyleI : salaryStyle;

            XSSFRow row = sheet.createRow(4 + i);
            row.setHeightInPoints(18);

            createStyledCell(row, 0, emp.getId() != null ? emp.getId().toString() : "", rowStyle);
            createStyledCell(row, 1, emp.getFirstName(), rowStyle);
            createStyledCell(row, 2, emp.getLastName(), rowStyle);
            createStyledCell(row, 3, emp.getEmail(), rowStyle);
            createStyledCell(row, 4, emp.getDepartment(), rowStyle);

            // Salary — numeric
            XSSFCell salaryCell = row.createCell(5);
            salaryCell.setCellValue(emp.getSalary() != null ? emp.getSalary().doubleValue() : 0);
            salaryCell.setCellStyle(salStyle);

            createStyledCell(row, 6,
                    emp.getDateOfJoining() != null ? emp.getDateOfJoining().format(dtf) : "", rowStyle);

            // Status with emoji
            String status = Boolean.TRUE.equals(emp.getActive()) ? "✅ Active" : "❌ Inactive";
            createStyledCell(row, 7, status, rowStyle);

            createStyledCell(row, 8,
                    emp.getCreatedAt() != null ? emp.getCreatedAt().format(dtfTs) : "", rowStyle);
        }

        // Totals row
        int totalsRowIdx = 4 + employees.size();
        XSSFRow totalsRow = sheet.createRow(totalsRowIdx);
        totalsRow.setHeightInPoints(20);
        XSSFCellStyle totalsStyle = createTotalsStyle(wb);

        sheet.addMergedRegion(new CellRangeAddress(totalsRowIdx, totalsRowIdx, 0, 3));
        XSSFCell totalLabel = totalsRow.createCell(0);
        totalLabel.setCellValue("📊  TOTAL: " + employees.size() + " employees");
        totalLabel.setCellStyle(totalsStyle);

        long active = employees.stream().filter(e -> Boolean.TRUE.equals(e.getActive())).count();
        sheet.addMergedRegion(new CellRangeAddress(totalsRowIdx, totalsRowIdx, 4, 8));
        XSSFCell totalInfo = totalsRow.createCell(4);
        totalInfo.setCellValue("✅ Active: " + active + "   ❌ Inactive: " + (employees.size() - active));
        totalInfo.setCellStyle(totalsStyle);

        // Auto-size and freeze
        for (int c = 0; c < EXPORT_HEADERS.length; c++) {
            sheet.autoSizeColumn(c);
            // add a bit of breathing room
            sheet.setColumnWidth(c, (int) (sheet.getColumnWidth(c) * 1.15));
        }
        sheet.createFreezePane(0, 4); // freeze title + header rows
        sheet.setAutoFilter(new CellRangeAddress(3, 3 + employees.size(), 0, EXPORT_HEADERS.length - 1));
    }

    // =========================================================================
    // Sheet 2 — Summary
    // =========================================================================

    private void buildSummarySheet(XSSFWorkbook wb, List<Employee> employees) {
        XSSFSheet sheet = wb.createSheet("📊 Summary");

        XSSFCellStyle titleStyle  = createTitleStyle(wb);
        XSSFCellStyle labelStyle  = createSummaryLabelStyle(wb);
        XSSFCellStyle valueStyle  = createSummaryValueStyle(wb);
        XSSFCellStyle deptHeader  = createHeaderStyle(wb);

        // Title
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        XSSFRow t = sheet.createRow(0);
        t.setHeightInPoints(36);
        XSSFCell tc = t.createCell(0);
        tc.setCellValue("📊  Report Summary");
        tc.setCellStyle(titleStyle);

        int row = 2;

        // Key metrics
        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Total Employees",
                String.valueOf(employees.size()));

        long activeCount   = employees.stream().filter(e -> Boolean.TRUE.equals(e.getActive())).count();
        long inactiveCount = employees.size() - activeCount;

        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Active Employees",
                activeCount + " ✅");
        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Inactive Employees",
                inactiveCount + " ❌");

        OptionalStats stats = computeStats(employees);
        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Average Salary",
                stats.avgSalary != null ? "$ " + String.format("%,.2f", stats.avgSalary) : "—");
        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Highest Salary",
                stats.maxSalary != null ? "$ " + String.format("%,.2f", stats.maxSalary)
                        + "  (" + stats.topEarner + ")" : "—");
        row = addSummaryRow(sheet, row, labelStyle, valueStyle, "Lowest Salary",
                stats.minSalary != null ? "$ " + String.format("%,.2f", stats.minSalary) : "—");
        row++;

        // Department breakdown header
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 3));
        XSSFRow dh = sheet.createRow(row++);
        dh.setHeightInPoints(20);
        XSSFCell dhc = dh.createCell(0);
        dhc.setCellValue("Department Breakdown");
        dhc.setCellStyle(deptHeader);

        // Column sub-headers
        XSSFRow subHeaders = sheet.createRow(row++);
        String[] sh = {"Department", "Total", "Active", "Inactive"};
        for (int c = 0; c < sh.length; c++) {
            XSSFCell cell = subHeaders.createCell(c);
            cell.setCellValue(sh[c]);
            cell.setCellStyle(deptHeader);
        }

        // Per-department rows
        Map<String, List<Employee>> byDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDepartment,
                         LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Employee>> entry : byDept.entrySet()) {
            String dept = entry.getKey();
            List<Employee> deptEmps = entry.getValue();
            long deptActive = deptEmps.stream().filter(e -> Boolean.TRUE.equals(e.getActive())).count();

            XSSFCellStyle deptRowStyle = createDeptSummaryStyle(wb, dept);

            XSSFRow deptRow = sheet.createRow(row++);
            deptRow.setHeightInPoints(18);
            createStyledCell(deptRow, 0, dept,                            deptRowStyle);
            createStyledCell(deptRow, 1, String.valueOf(deptEmps.size()), deptRowStyle);
            createStyledCell(deptRow, 2, deptActive + " ✅",              deptRowStyle);
            createStyledCell(deptRow, 3, (deptEmps.size() - deptActive) + " ❌", deptRowStyle);
        }

        for (int c = 0; c < 4; c++) {
            sheet.autoSizeColumn(c);
            sheet.setColumnWidth(c, (int) (sheet.getColumnWidth(c) * 1.2));
        }
    }

    private int addSummaryRow(XSSFSheet sheet, int rowIdx,
                               XSSFCellStyle labelStyle, XSSFCellStyle valueStyle,
                               String label, String value) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        XSSFCell lc = row.createCell(0);
        lc.setCellValue(label);
        lc.setCellStyle(labelStyle);
        XSSFCell vc = row.createCell(1);
        vc.setCellValue(value);
        vc.setCellStyle(valueStyle);
        return rowIdx + 1;
    }

    // =========================================================================
    // Style factory methods
    // =========================================================================

    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)31, (byte)78, (byte)121}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setWrapText(false);
        return style;
    }

    private XSSFCellStyle createSubTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)46,(byte)117,(byte)182}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setColor(new XSSFColor(new byte[]{(byte)189,(byte)215,(byte)238}, null));
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)31,(byte)78,(byte)121}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBordersWhite(style);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb, String dept,
                                           boolean inactive, boolean altRow) {
        XSSFCellStyle style = wb.createCellStyle();
        byte[] color;
        if (inactive) {
            color = rgb(230, 230, 230);
        } else {
            color = DEPT_COLORS.getOrDefault(dept, DEFAULT_ROW_COLOR);
        }
        style.setFillForegroundColor(new XSSFColor(color, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBordersGray(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        XSSFFont font = wb.createFont();
        if (inactive) {
            font.setColor(new XSSFColor(new byte[]{(byte)160,(byte)160,(byte)160}, null));
            font.setStrikeout(true);
        }
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createSalaryStyle(XSSFWorkbook wb, boolean inactive) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("$#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBordersGray(style);
        XSSFFont font = wb.createFont();
        if (inactive) {
            font.setColor(new XSSFColor(new byte[]{(byte)160,(byte)160,(byte)160}, null));
            font.setStrikeout(true);
        } else {
            font.setBold(true);
            font.setColor(new XSSFColor(new byte[]{(byte)0,(byte)97,(byte)0}, null));
        }
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createTotalsStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)31,(byte)78,(byte)121}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        setBordersWhite(style);
        return style;
    }

    private XSSFCellStyle createSummaryLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)91,(byte)155,(byte)213}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBordersWhite(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createSummaryValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)235,(byte)245,(byte)255}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBordersGray(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createDeptSummaryStyle(XSSFWorkbook wb, String dept) {
        XSSFCellStyle style = wb.createCellStyle();
        byte[] color = DEPT_COLORS.getOrDefault(dept, DEFAULT_ROW_COLOR);
        style.setFillForegroundColor(new XSSFColor(color, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBordersGray(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private void setBordersWhite(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor white = new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null);
        style.setBottomBorderColor(white);
        style.setTopBorderColor(white);
        style.setLeftBorderColor(white);
        style.setRightBorderColor(white);
    }

    private void setBordersGray(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor gray = new XSSFColor(new byte[]{(byte)200,(byte)210,(byte)220}, null);
        style.setBottomBorderColor(gray);
        style.setTopBorderColor(gray);
        style.setLeftBorderColor(gray);
        style.setRightBorderColor(gray);
    }

    // =========================================================================
    // Private helpers — Import
    // =========================================================================

    private void processRow(XSSFRow row, int humanRowNumber, ImportResultDto result) {
        StringBuilder rowErrors = new StringBuilder();

        String firstName  = CellExtractorUtil.getString(row.getCell(COL_FIRST_NAME));
        String lastName   = CellExtractorUtil.getString(row.getCell(COL_LAST_NAME));
        String email      = CellExtractorUtil.getString(row.getCell(COL_EMAIL));
        String department = CellExtractorUtil.getString(row.getCell(COL_DEPARTMENT));
        double salaryRaw  = CellExtractorUtil.getDouble(row.getCell(COL_SALARY));
        String joinDate   = CellExtractorUtil.getString(row.getCell(COL_JOIN_DATE));
        boolean active    = CellExtractorUtil.getBoolean(row.getCell(COL_ACTIVE));

        LocalDate parsedDate = parseDate(joinDate, humanRowNumber, rowErrors);

        EmployeeRequestDto dto = new EmployeeRequestDto(
                firstName, lastName, email, department,
                BigDecimal.valueOf(salaryRaw).setScale(2, RoundingMode.HALF_UP),
                parsedDate, active);

        Set<ConstraintViolation<EmployeeRequestDto>> violations = validator.validate(dto);
        for (ConstraintViolation<EmployeeRequestDto> v : violations) {
            if (rowErrors.length() > 0) rowErrors.append("; ");
            rowErrors.append(v.getPropertyPath()).append(" – ").append(v.getMessage());
        }

        if (rowErrors.length() > 0) {
            result.addError(humanRowNumber, rowErrors.toString());
            return;
        }

        try {
            employeeRepository.save(Employee.builder()
                    .firstName(dto.firstName()).lastName(dto.lastName())
                    .email(dto.email()).department(dto.department())
                    .salary(dto.salary()).dateOfJoining(dto.dateOfJoining())
                    .active(dto.active()).build());
            result.incrementSuccess();
        } catch (Exception e) {
            result.addError(humanRowNumber, "Database error: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String value, int rowNum, StringBuilder errors) {
        if (value == null || value.isBlank()) {
            errors.append("dateOfJoining – must not be blank");
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.append("dateOfJoining – invalid format '").append(value)
                  .append("' (expected YYYY-MM-DD)");
            return null;
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c <= COL_ACTIVE; c++) {
            if (!CellExtractorUtil.isEmpty(row.getCell(c))) return false;
        }
        return true;
    }

    private void validateFileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(XLSX_EXTENSION)) {
            throw new InvalidFileFormatException("Only .xlsx files are supported. Received: " + name);
        }
        if (file.isEmpty()) {
            throw new InvalidFileFormatException("Uploaded file is empty");
        }
    }

    private List<Employee> fetchEmployeesForExport(String department, Boolean active) {
        if (department != null && active != null) {
            return employeeRepository.findByDepartmentAndActive(
                    department, active, org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else if (department != null) {
            return employeeRepository.findByDepartment(department);
        } else if (active != null) {
            return active ? employeeRepository.findByActiveTrue() : employeeRepository.findAll();
        }
        return employeeRepository.findAll();
    }

    private void createStyledCell(XSSFRow row, int col, String value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    // =========================================================================
    // Stats helper
    // =========================================================================

    private OptionalStats computeStats(List<Employee> employees) {
        OptionalStats s = new OptionalStats();
        List<Employee> withSalary = employees.stream()
                .filter(e -> e.getSalary() != null).toList();
        if (withSalary.isEmpty()) return s;

        s.avgSalary = withSalary.stream()
                .map(Employee::getSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(withSalary.size()), 2, RoundingMode.HALF_UP);

        Employee top = withSalary.stream()
                .max((a, b) -> a.getSalary().compareTo(b.getSalary())).orElse(null);
        if (top != null) {
            s.maxSalary = top.getSalary();
            s.topEarner = top.getFirstName() + " " + top.getLastName();
        }

        s.minSalary = withSalary.stream()
                .map(Employee::getSalary)
                .min(BigDecimal::compareTo).orElse(null);
        return s;
    }

    private static class OptionalStats {
        BigDecimal avgSalary, maxSalary, minSalary;
        String topEarner;
    }

    private static byte[] rgb(int r, int g, int b) {
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
}
