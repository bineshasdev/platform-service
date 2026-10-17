package com.flowbinary.platformservice.dto.dataimport;

/**
 * Per-row result of a bulk import.
 */
public enum ImportOutcome {
    CREATED,
    UPDATED,
    SKIPPED,
    FAILED
}
