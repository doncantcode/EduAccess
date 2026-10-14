package com.eduaccess.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes {@link Report} tables to CSV (UTF-8 with a BOM, so Excel shows Filipino names correctly) or XLSX. */
public final class FileExportService {

    private static final int COLUMN_WIDTH = 22 * 256;

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

    public void writeXlsx(Report report, Path target) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet(sheetName(report.name()));

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int col = 0; col < report.headers().size(); col++) {
                var cell = header.createCell(col);
                cell.setCellValue(report.headers().get(col));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(col, COLUMN_WIDTH);
            }

            int rowIndex = 1;
            for (List<Object> values : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < values.size(); col++) {
                    Object value = values.get(col);
                    var cell = row.createCell(col);
                    if (value instanceof Number number) {
                        cell.setCellValue(number instanceof BigDecimal big ? big.doubleValue() : number.doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
        } catch (IOException e) {
            throw new ServiceException("Could not write the Excel file: " + e.getMessage(), e);
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
