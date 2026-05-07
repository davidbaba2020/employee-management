package com.assessment.employee.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for safely extracting typed values from Apache POI {@link Cell} objects.
 *
 * <p>Excel cells can hold numeric, string, boolean, blank, or formula values.
 * These helper methods normalise them to Java types without throwing NPEs or
 * type-mismatch exceptions.
 */
public final class CellExtractorUtil {

    private static final Logger log = LoggerFactory.getLogger(CellExtractorUtil.class);

    // Utility class — prevent instantiation
    private CellExtractorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts a {@link String} value from a cell, handling all cell types.
     *
     * @param cell the POI cell (may be null)
     * @return string value, or {@code ""} if the cell is null or blank
     */
    public static String getString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // Numeric cells holding whole numbers should not show ".0"
                double val = cell.getNumericCellValue();
                yield (val == Math.floor(val) && !Double.isInfinite(val))
                        ? String.valueOf((long) val)
                        : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> evaluateFormulaCell(cell);
            case BLANK, _NONE -> "";
            default -> "";
        };
    }

    /**
     * Extracts a {@code double} from a numeric cell.
     *
     * @param cell the POI cell (may be null)
     * @return the numeric value, or {@code 0.0} if null/blank/non-numeric
     */
    public static double getDouble(Cell cell) {
        if (cell == null) {
            return 0.0;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                log.warn("Cannot parse '{}' as a number at column {}", cell.getStringCellValue(), cell.getColumnIndex());
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Extracts a {@code boolean} from a boolean cell.
     * String values of "true" / "false" (case-insensitive) are also accepted.
     *
     * @param cell the POI cell (may be null)
     * @return boolean value; defaults to {@code true} when blank/unrecognised
     */
    public static boolean getBoolean(Cell cell) {
        if (cell == null) {
            return true;
        }

        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING  -> Boolean.parseBoolean(cell.getStringCellValue().trim());
            case NUMERIC -> cell.getNumericCellValue() != 0;
            default      -> true;
        };
    }

    /**
     * Checks whether a cell is effectively empty (null, blank, or whitespace-only string).
     *
     * @param cell the POI cell to check
     * @return {@code true} if the cell has no meaningful value
     */
    public static boolean isEmpty(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return true;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().isBlank();
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String evaluateFormulaCell(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING  -> cell.getRichStringCellValue().getString().trim();
                case NUMERIC -> {
                    double val = cell.getNumericCellValue();
                    yield DateUtil.isCellDateFormatted(cell)
                            ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                            : String.valueOf(val);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default      -> "";
            };
        } catch (Exception e) {
            log.warn("Formula evaluation failed for cell at col {}: {}", cell.getColumnIndex(), e.getMessage());
            return "";
        }
    }
}
