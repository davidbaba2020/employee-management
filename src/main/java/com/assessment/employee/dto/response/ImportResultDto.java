package com.assessment.employee.dto.response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable accumulator for the result of a bulk Excel import.
 *
 * <p>This class is intentionally <em>not</em> a record because it is built
 * incrementally — one row at a time — during the import loop.  Once
 * processing is complete, the final state is immutable from the caller's
 * perspective (there is no reason to mutate it after the service returns it).
 *
 * <p>Error messages are stored in the format {@code "Row N: fieldName – reason"}.
 */
public final class ImportResultDto {

    private int successCount;
    private int failureCount;
    private final List<String> errors = new ArrayList<>();

    public ImportResultDto() {}

    // -------------------------------------------------------------------------
    // Mutation (import loop)
    // -------------------------------------------------------------------------

    /** Increments the success counter. */
    public void incrementSuccess() {
        successCount++;
    }

    /**
     * Records a row-level failure and increments the failure counter.
     *
     * @param rowNumber 1-based row number in the Excel sheet
     * @param message   human-readable description of the failure
     */
    public void addError(int rowNumber, String message) {
        errors.add("Row " + rowNumber + ": " + message);
        failureCount++;
    }

    // -------------------------------------------------------------------------
    // Read accessors
    // -------------------------------------------------------------------------

    public int getSuccessCount() { return successCount; }

    public int getFailureCount() { return failureCount; }

    /** Total rows processed = success + failures. */
    public int getTotalRows()    { return successCount + failureCount; }

    /** Unmodifiable view of accumulated error messages. */
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }
}
