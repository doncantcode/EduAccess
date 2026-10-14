package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileExportServiceTest {

    @TempDir
    Path tempDir;

    private final FileExportService exporter = new FileExportService();

    private static final Report SAMPLE = new Report(
            "Borrowing Log",
            List.of("Borrower", "Item", "Fee (PHP)"),
            List.of(
                    Arrays.asList("Ana, Dela Cruz", "Casio \"fx-991EX\"", new BigDecimal("15.00")),
                    Arrays.asList("Ben Santos", "Tablet\nSamsung", null)));

    @Test
    void csvIsUtf8WithBomAndEscapesSpecialCharacters() throws Exception {
        Path target = tempDir.resolve("log.csv");

        exporter.writeCsv(SAMPLE, target);

        String text = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿"));
        assertTrue(text.contains("Borrower,Item,Fee (PHP)\r\n"));
        assertTrue(text.contains("\"Ana, Dela Cruz\",\"Casio \"\"fx-991EX\"\"\",15.00\r\n"));
        assertTrue(text.contains("Ben Santos,\"Tablet\nSamsung\","));
    }

    @Test
    void excelWorkbookHasTheHeaderAndNumericFees() throws Exception {
        Path target = tempDir.resolve("log.xlsx");

        exporter.writeXlsx(SAMPLE, target);

        try (InputStream in = Files.newInputStream(target);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("Borrowing Log");
            assertEquals("Borrower", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Ana, Dela Cruz", sheet.getRow(1).getCell(0).getStringCellValue());

            Row first = sheet.getRow(1);
            Cell fee = first.getCell(2);
            assertEquals(15.0, fee.getNumericCellValue(), 0.0001);
        }
    }

    @Test
    void sheetNamesAreShortenedAndCleaned() {
        assertEquals("Impact  Report", FileExportService.sheetName("Impact [Report]"));
        assertEquals(31, FileExportService.sheetName("x".repeat(40)).length());
    }
}
