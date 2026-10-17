package com.flowbinary.platformservice.dto.dataimport;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Outcome for a single spreadsheet row.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportRowResult {

    /** 1-based row number in the sheet (header is row 1). */
    private int row;

    private ImportOutcome outcome;

    /** Natural key of the row — clinic slug, doctor email, "doctorEmail @ clinicSlug". */
    private String reference;

    /** Free-text detail — the error on FAILED, or what changed on CREATED/UPDATED. */
    private String message;

    /**
     * Temporary password generated for a newly created login (clinic admin / doctor).
     * Only present on CREATED rows where no password column was supplied.
     */
    private String generatedPassword;
}
