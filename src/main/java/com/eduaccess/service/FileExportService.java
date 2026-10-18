package com.eduaccess.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes {@link Report} tables to CSV (UTF-8 with a BOM, so Excel shows Filipino names correctly) or XLSX. */
public final class FileExportService {

    private static final int MAX_COLUMN_CHARS = 60;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);

    public void writeCsv(Report report, Path target) {
        try (Writer out = new FileWriter(target.toFile(), StandardCharsets.UTF_8)) {
            out.write('﻿');
            writeCsvRow(out, report.headers());
            for (List<Object> row : report.rows()) {
                writeCsvRow(out, row);
            }
        } catch (IOException e) {
            throw new ServiceException("Could not write the CSV file: " + e.getMessage(), e);
        }
    }

    /** One report as a single-sheet workbook. */
    public void writeXlsx(Report report, Path target) {
        writeWorkbook(List.of(report), null, target);
    }

    /**
     * Several reports in one workbook, one sheet each. With a title, a cover sheet comes first listing every sheet,
     * its row count and when the file was made. Headers stay visible while scrolling and have filter arrows.
     */
    public void writeWorkbook(List<Report> reports, String title, Path target) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(target)) {
            Styles styles = new Styles(workbook);
            if (title != null) {
                cover(workbook, styles, title, reports);
            }
            Set<String> used = new HashSet<>();
            for (Report report : reports) {
                writeSheet(workbook, styles, report, uniqueName(sheetName(report.name()), used));
            }
            workbook.write(out);
        } catch (IOException e) {
            throw new ServiceException("Could not write the Excel file: " + e.getMessage(), e);
        }
    }

    private static void writeSheet(XSSFWorkbook workbook, Styles styles, Report report, String name) {
        Sheet sheet = workbook.createSheet(name);
        int columns = report.headers().size();
        int[] widths = new int[columns];

        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int col = 0; col < columns; col++) {
            Cell cell = header.createCell(col);
            cell.setCellValue(report.headers().get(col));
            cell.setCellStyle(styles.header);
            widths[col] = report.headers().get(col).length() + 4;
        }

        int rowIndex = 1;
        for (List<Object> values : report.rows()) {
            Row row = sheet.createRow(rowIndex++);
            for (int col = 0; col < Math.min(columns, values.size()); col++) {
                Object value = values.get(col);
                Cell cell = row.createCell(col);
                if (value instanceof Number number) {
                    cell.setCellValue(number instanceof BigDecimal big ? big.doubleValue() : number.doubleValue());
                    boolean money = report.headers().get(col).contains("(PHP)");
                    cell.setCellStyle(money ? styles.money : number instanceof Double || number instanceof BigDecimal
                            ? styles.decimal : styles.whole);
                    widths[col] = Math.max(widths[col], 12);
                } else if (value != null) {
                    String text = value.toString();
                    cell.setCellValue(text);
                    widths[col] = Math.max(widths[col], Math.min(text.length() + 2, MAX_COLUMN_CHARS));
                }
            }
        }
        for (int col = 0; col < columns; col++) {
            sheet.setColumnWidth(col, Math.min(widths[col], MAX_COLUMN_CHARS) * 256);
        }
        sheet.createFreezePane(0, 1);
        if (columns > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, columns - 1));
        }
    }

    private static void cover(XSSFWorkbook workbook, Styles styles, String title, List<Report> reports) {
        Sheet sheet = workbook.createSheet("About");
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title);
        sheet.createRow(1).createCell(0).setCellValue("Created " + LocalDateTime.now().format(STAMP)
                + " by EduAccess. Passwords and guardian contact details are never exported.");
        sheet.createRow(2).createCell(0).setCellValue("Personal data inside: handle and share this file under "
                + "RA 10173, the Data Privacy Act.");
        Row header = sheet.createRow(4);
        String[] heads = {"Sheet", "Rows"};
        for (int col = 0; col < heads.length; col++) {
            Cell cell = header.createCell(col);
            cell.setCellValue(heads[col]);
            cell.setCellStyle(styles.header);
        }
        int index = 5;
        for (Report report : reports) {
            Row row = sheet.createRow(index++);
            row.createCell(0).setCellValue(report.name());
            Cell count = row.createCell(1);
            count.setCellValue(report.rows().size());
            count.setCellStyle(styles.whole);
        }
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 12 * 256);
    }

    /** Sheet names must be unique within a workbook. */
    private static String uniqueName(String name, Set<String> used) {
        String candidate = name;
        for (int n = 2; !used.add(candidate.toLowerCase()); n++) {
            String suffix = " (" + n + ")";
            candidate = (name.length() + suffix.length() > 31 ? name.substring(0, 31 - suffix.length()) : name) + suffix;
        }
        return candidate;
    }

    /** The cell styles shared by every sheet in a workbook. */
    private static final class Styles {
        final CellStyle header;
        final CellStyle title;
        final CellStyle money;
        final CellStyle decimal;
        final CellStyle whole;

        Styles(XSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();
            header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            title = workbook.createCellStyle();
            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 16);
            title.setFont(big);

            money = workbook.createCellStyle();
            money.setDataFormat(format.getFormat("#,##0.00"));
            decimal = workbook.createCellStyle();
            decimal.setDataFormat(format.getFormat("0.0#"));
            whole = workbook.createCellStyle();
            whole.setDataFormat(format.getFormat("0"));
        }
    }

    private static void writeCsvRow(Writer out, List<?> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            Object value = cells.get(i);
            line.append(csvEscape(value == null ? "" : value.toString()));
        }
        out.write(line.append("\r\n").toString());
    }

    /** Quotes a field that contains a comma, a quote or a line break, and doubles any quotes inside it. */
    static String csvEscape(String text) {
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** Excel sheet names are at most 31 characters and cannot contain []:*?/\ . */
    static String sheetName(String name) {
        String cleaned = name.replaceAll("[\\[\\]:*?/\\\\]", " ").strip();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
