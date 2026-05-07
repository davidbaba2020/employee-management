package com.assessment.employee.service.impl;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.assessment.employee.entity.Employee;
import com.assessment.employee.repository.EmployeeRepository;
import com.assessment.employee.service.PdfService;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * OpenPDF-based implementation of {@link PdfService}.
 *
 * <p>Generates a colourful, decorated PDF report:
 * <ul>
 *   <li>Full-width banner header with company name and report title</li>
 *   <li>Summary stats row — Total / Active / Inactive / Departments</li>
 *   <li>Data table with department-coloured rows</li>
 *   <li>Active badge (green) / Inactive badge (red) per row</li>
 *   <li>"Page N of M" footer on every page</li>
 * </ul>
 */
@Service
public class PdfServiceImpl implements PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfServiceImpl.class);

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String COMPANY_NAME     = "Acme Corporation";

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final Color BANNER_DARK   = new Color(31,  78, 121);
    private static final Color BANNER_MID    = new Color(46, 117, 182);
    private static final Color BANNER_LIGHT  = new Color(91, 164, 207);
    private static final Color WHITE         = Color.WHITE;
    private static final Color BORDER_SUBTLE = new Color(210, 218, 226);

    // Status badge colours
    private static final Color ACTIVE_BG    = new Color(39, 174,  96);
    private static final Color INACTIVE_BG  = new Color(192,  57,  43);
    private static final Color INACTIVE_TXT = new Color(160, 160, 160);

    // Summary card accent colours
    private static final Color CARD_TOTAL   = new Color( 52, 152, 219);
    private static final Color CARD_ACTIVE  = new Color( 39, 174,  96);
    private static final Color CARD_INACTIVE= new Color(231,  76,  60);
    private static final Color CARD_DEPT    = new Color(155,  89, 182);

    // Department row colours (light pastels)
    private static final Map<String, Color> DEPT_COLORS = Map.of(
        "Engineering", new Color(189, 215, 238),
        "HR",          new Color(198, 224, 180),
        "Finance",     new Color(255, 242, 204),
        "Marketing",   new Color(231, 215, 244),
        "Sales",       new Color(252, 228, 214),
        "Operations",  new Color(208, 236, 244),
        "Intern",      new Color(252, 228, 236)
    );
    private static final Color DEPT_DEFAULT = new Color(242, 242, 242);

    // Department badge (text) colours — darker counterparts
    private static final Map<String, Color> DEPT_TEXT_COLORS = Map.of(
        "Engineering", new Color( 31,  78, 121),
        "HR",          new Color( 55,  86,  35),
        "Finance",     new Color(127,  96,   0),
        "Marketing",   new Color( 75,   0, 130),
        "Sales",       new Color(132,  60,  12),
        "Operations",  new Color( 31,  97, 120),
        "Intern",      new Color(118,  38,  65)
    );

    private static final float[] COL_WIDTHS = {1.2f, 3f, 4f, 2.8f, 2.5f, 2.5f, 1.8f};

    private final EmployeeRepository employeeRepository;

    public PdfServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    public void generateEmployeePdfReport(HttpServletResponse response) {
        List<Employee> employees = employeeRepository.findAll();
        String filename = "employee_report_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".pdf";

        response.setContentType(PDF_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        log.info("Generating PDF report for {} employees (HTTP stream)", employees.size());
        try {
            buildPdf(employees, response.getOutputStream());
        } catch (IOException e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    @Override
    public byte[] generateEmployeePdfReportAsBytes() {
        List<Employee> employees = employeeRepository.findAll();
        log.info("Generating PDF report for {} employees (byte array)", employees.size());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        buildPdf(employees, baos);
        return baos.toByteArray();
    }

    // =========================================================================
    // Core builder — writes to any OutputStream
    // =========================================================================

    private void buildPdf(List<Employee> employees, OutputStream out) {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 48, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageFooterEvent());
            document.open();

            writeBanner(document, writer, employees.size());
            writeSummaryCards(document, employees);
            writeTable(document, employees);

            document.close();
        } catch (DocumentException e) {
            log.error("PDF build error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build PDF", e);
        }
    }

    // =========================================================================
    // Banner — three-tone gradient simulation
    // =========================================================================

    private void writeBanner(Document doc, PdfWriter writer, int totalRecords)
            throws DocumentException {

        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle page = doc.getPageSize();
        float bannerTop    = page.getTop(doc.topMargin() - 42);
        float bannerBottom = bannerTop - 70;
        float w = page.getWidth() - doc.leftMargin() - doc.rightMargin();
        float x = doc.leftMargin();

        // Three horizontal bands to simulate gradient
        drawRect(canvas, x,            bannerBottom, w * 0.34f, 70, BANNER_DARK);
        drawRect(canvas, x + w * 0.34f, bannerBottom, w * 0.33f, 70, BANNER_MID);
        drawRect(canvas, x + w * 0.67f, bannerBottom, w * 0.33f, 70, BANNER_LIGHT);

        // Company name
        Font companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, WHITE);
        Paragraph company = new Paragraph(COMPANY_NAME, companyFont);
        company.setAlignment(Element.ALIGN_CENTER);
        company.setSpacingBefore(8);
        doc.add(company);

        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(189, 215, 238));
        Paragraph subtitle = new Paragraph("Employee Directory Report", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(2);
        doc.add(subtitle);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(220, 230, 240));
        String meta = "Generated: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))
                + "   ·   " + totalRecords + " records";
        Paragraph metaPara = new Paragraph(meta, metaFont);
        metaPara.setAlignment(Element.ALIGN_CENTER);
        metaPara.setSpacingBefore(3);
        metaPara.setSpacingAfter(16);
        doc.add(metaPara);
    }

    private void drawRect(PdfContentByte cb, float x, float y, float w, float h, Color color) {
        cb.setColorFill(color);
        cb.rectangle(x, y, w, h);
        cb.fill();
    }

    // =========================================================================
    // Summary cards
    // =========================================================================

    private void writeSummaryCards(Document doc, List<Employee> employees)
            throws DocumentException {

        long activeCount   = employees.stream().filter(e -> Boolean.TRUE.equals(e.getActive())).count();
        long inactiveCount = employees.size() - activeCount;
        long deptCount     = employees.stream().map(Employee::getDepartment).distinct().count();

        PdfPTable cards = new PdfPTable(4);
        cards.setWidthPercentage(100);
        cards.setSpacingBefore(4);
        cards.setSpacingAfter(14);

        addSummaryCard(cards, "Total",       String.valueOf(employees.size()), "Employees",  CARD_TOTAL);
        addSummaryCard(cards, "Active",      String.valueOf(activeCount),       "Active",     CARD_ACTIVE);
        addSummaryCard(cards, "Inactive",    String.valueOf(inactiveCount),     "Inactive",   CARD_INACTIVE);
        addSummaryCard(cards, "Departments", String.valueOf(deptCount),         "Unique depts", CARD_DEPT);

        doc.add(cards);
    }

    private void addSummaryCard(PdfPTable table, String label, String value,
                                 String sub, Color accent) {
        PdfPCell card = new PdfPCell();
        card.setPadding(10);
        card.setBorderColor(BORDER_SUBTLE);
        card.setBackgroundColor(WHITE);

        // Accent bar at the top
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPCell accentBar = new PdfPCell(new Phrase(" "));
        accentBar.setFixedHeight(5);
        accentBar.setBackgroundColor(accent);
        accentBar.setBorder(Rectangle.NO_BORDER);
        inner.addCell(accentBar);

        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, accent);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setPaddingTop(6);
        valueCell.setBorder(Rectangle.NO_BORDER);
        inner.addCell(valueCell);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        PdfPCell labelCell = new PdfPCell(new Phrase(label.toUpperCase(), labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        labelCell.setPaddingBottom(6);
        labelCell.setBorder(Rectangle.NO_BORDER);
        inner.addCell(labelCell);

        card.addElement(inner);
        table.addCell(card);
    }

    // =========================================================================
    // Employee table
    // =========================================================================

    private void writeTable(Document doc, List<Employee> employees) throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS.length);
        table.setWidthPercentage(100);
        table.setWidths(COL_WIDTHS);
        table.setSpacingBefore(4);
        table.setHeaderRows(1);

        writeTableHeader(table);
        for (int i = 0; i < employees.size(); i++) {
            writeTableRow(table, employees.get(i), i);
        }
        doc.add(table);
    }

    private void writeTableHeader(PdfPTable table) {
        String[] headers = {"ID", "Full Name", "Email", "Department", "Salary", "Joined", "Status"};
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WHITE);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hf));
            cell.setBackgroundColor(BANNER_DARK);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(7);
            cell.setBorderColor(BANNER_MID);
            table.addCell(cell);
        }
    }

    private void writeTableRow(PdfPTable table, Employee emp, int rowIndex) {
        boolean inactive = Boolean.FALSE.equals(emp.getActive());
        Color deptBg = getDeptColor(emp.getDepartment());
        Color rowBg  = rowIndex % 2 == 0 ? WHITE : deptBg;

        Font dataFont = inactive
                ? FontFactory.getFont(FontFactory.HELVETICA, 8, Font.STRIKETHRU, INACTIVE_TXT)
                : FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        Font salaryFont = inactive
                ? FontFactory.getFont(FontFactory.HELVETICA, 8, Font.STRIKETHRU, INACTIVE_TXT)
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0, 100, 0));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy");

        addDataCell(table, String.valueOf(emp.getId()), dataFont, rowBg, Element.ALIGN_CENTER);
        addDataCell(table, emp.getFirstName() + " " + emp.getLastName(), dataFont, rowBg, Element.ALIGN_LEFT);
        addDataCell(table, emp.getEmail(), dataFont, rowBg, Element.ALIGN_LEFT);

        // Department badge cell
        addDeptCell(table, emp.getDepartment(), rowBg, inactive);

        String salary = emp.getSalary() != null
                ? "$ " + String.format("%,.0f", emp.getSalary()) : "—";
        addDataCell(table, salary, salaryFont, rowBg, Element.ALIGN_RIGHT);

        String joined = emp.getDateOfJoining() != null ? emp.getDateOfJoining().format(dtf) : "—";
        addDataCell(table, joined, dataFont, rowBg, Element.ALIGN_CENTER);

        // Status badge cell
        addStatusCell(table, emp.getActive(), rowBg);
    }

    private void addDataCell(PdfPTable table, String text, Font font,
                              Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_SUBTLE);
        table.addCell(cell);
    }

    private void addDeptCell(PdfPTable table, String dept, Color rowBg, boolean inactive) {
        Color badgeBg   = inactive ? new Color(220, 220, 220) : getDeptColor(dept);
        Color badgeText = inactive ? INACTIVE_TXT : getDeptTextColor(dept);
        Font  badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, badgeText);

        PdfPCell cell = new PdfPCell(new Phrase(dept, badgeFont));
        cell.setBackgroundColor(badgeBg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_SUBTLE);
        table.addCell(cell);
    }

    private void addStatusCell(PdfPTable table, Boolean active, Color rowBg) {
        boolean isActive = Boolean.TRUE.equals(active);
        String  label    = isActive ? "Active" : "Inactive";
        Color   badgeBg  = isActive ? ACTIVE_BG : INACTIVE_BG;
        Font    badgeFont= FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, WHITE);

        PdfPCell cell = new PdfPCell(new Phrase(label, badgeFont));
        cell.setBackgroundColor(badgeBg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_SUBTLE);
        table.addCell(cell);
    }

    // =========================================================================
    // Colour helpers
    // =========================================================================

    private Color getDeptColor(String dept) {
        return DEPT_COLORS.getOrDefault(dept, DEPT_DEFAULT);
    }

    private Color getDeptTextColor(String dept) {
        return DEPT_TEXT_COLORS.getOrDefault(dept, new Color(60, 60, 60));
    }

    // =========================================================================
    // Page footer: "Page N of M · Company — Confidential"
    // =========================================================================

    private static class PageFooterEvent extends PdfPageEventHelper {

        private PdfTemplate totalTemplate;
        private final Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTemplate = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb  = writer.getDirectContent();
            Rectangle       pg = document.getPageSize();
            float bottom = pg.getBottom(28);

            // Left: confidentiality notice
            cb.beginText();
            cb.setFontAndSize(footerFont.getBaseFont(), 8);
            cb.setColorFill(new Color(180, 180, 180));
            cb.showTextAligned(Element.ALIGN_LEFT,
                    COMPANY_NAME + " — Confidential | Do not distribute",
                    pg.getLeft(28), bottom, 0);
            cb.endText();

            // Right: "Page N of …"
            String pageStr = "Page " + writer.getPageNumber() + " of ";
            float tw = footerFont.getBaseFont().getWidthPoint(pageStr, 8);

            cb.beginText();
            cb.setFontAndSize(footerFont.getBaseFont(), 8);
            cb.setColorFill(Color.GRAY);
            cb.showTextAligned(Element.ALIGN_RIGHT, pageStr,
                    pg.getRight(28) - 30, bottom, 0);
            cb.endText();

            cb.addTemplate(totalTemplate, pg.getRight(28) - 30 + tw - tw, bottom);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalTemplate.beginText();
            totalTemplate.setFontAndSize(footerFont.getBaseFont(), 8);
            totalTemplate.setColorFill(Color.GRAY);
            totalTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
            totalTemplate.endText();
        }
    }
}
