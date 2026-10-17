package com.flowbinary.platformservice.service.dataimport;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal, dependency-light reader for the first sheet of an {@code .xlsx} /
 * {@code .xls} workbook. Columns are addressed by header name (case-insensitive,
 * whitespace-trimmed); column order does not matter.
 */
public final class SpreadsheetReader {

    private SpreadsheetReader() {}

    /** A single data row exposed as typed accessors over header → cell-text. */
    public static final class Row {
        private final int number;
        private final Map<String, String> values;

        private Row(int number, Map<String, String> values) {
            this.number = number;
            this.values = values;
        }

        /** 1-based row number as shown in the spreadsheet. */
        public int number() {
            return number;
        }

        public String get(String header) {
            String v = values.get(normalize(header));
            return (v == null || v.isBlank()) ? null : v.trim();
        }

        public String require(String header) {
            String v = get(header);
            if (v == null) {
                throw new IllegalArgumentException("Missing required value for column '" + header + "'");
            }
            return v;
        }

        public boolean getBoolean(String header) {
            String v = get(header);
            return v != null && (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes")
                    || v.equals("1") || v.equalsIgnoreCase("y"));
        }

        public Integer getInt(String header) {
            String v = get(header);
            return v == null ? null : new BigDecimal(v).intValueExact();
        }

        public BigDecimal getDecimal(String header) {
            String v = get(header);
            return v == null ? null : new BigDecimal(v);
        }

        public LocalDate getDate(String header) {
            String v = get(header);
            return v == null ? null : LocalDate.parse(v);
        }

        /** Splits on comma / semicolon / pipe; returns null when blank. */
        public List<String> getList(String header) {
            String v = get(header);
            if (v == null) return null;
            return Arrays.stream(v.split("[,;|]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    /**
     * @param requiredHeaders headers that must be present in the sheet (validated up front)
     * @throws IllegalArgumentException if the sheet is empty or a required header is missing
     */
    public static List<Row> read(InputStream in, List<String> requiredHeaders) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("The spreadsheet is empty");
            }

            org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The spreadsheet has no header row");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<Integer, String> headerByCol = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                String name = normalize(cellText(cell, formatter, evaluator));
                if (!name.isEmpty()) {
                    headerByCol.put(cell.getColumnIndex(), name);
                }
            }

            for (String required : requiredHeaders) {
                if (!headerByCol.containsValue(normalize(required))) {
                    throw new IllegalArgumentException("Missing required column: '" + required + "'");
                }
            }

            List<Row> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = sheet.getFirstRowNum() + 1; r <= lastRow; r++) {
                org.apache.poi.ss.usermodel.Row sheetRow = sheet.getRow(r);
                if (sheetRow == null) continue;

                Map<String, String> values = new LinkedHashMap<>();
                boolean anyValue = false;
                for (Map.Entry<Integer, String> h : headerByCol.entrySet()) {
                    Cell cell = sheetRow.getCell(h.getKey(), org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String text = cell == null ? "" : cellText(cell, formatter, evaluator).trim();
                    if (!text.isEmpty()) anyValue = true;
                    values.put(h.getValue(), text);
                }
                if (anyValue) {
                    rows.add(new Row(r + 1, values)); // 1-based, matches spreadsheet UI
                }
            }
            return rows;
        }
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();

        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield new BigDecimal(Double.toString(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
            }
            case BLANK -> "";
            default -> formatter.formatCellValue(cell, evaluator);
        };
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}
