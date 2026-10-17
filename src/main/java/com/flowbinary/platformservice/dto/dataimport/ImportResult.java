package com.flowbinary.platformservice.dto.dataimport;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result of a bulk import. Rows are processed independently — a
 * failure on one row does not abort the rest.
 */
@Getter
public class ImportResult {

    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int failed;

    private final List<ImportRowResult> rows = new ArrayList<>();

    public void add(ImportRowResult row) {
        rows.add(row);
        total++;
        switch (row.getOutcome()) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case SKIPPED -> skipped++;
            case FAILED  -> failed++;
        }
    }
}
