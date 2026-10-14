package com.eduaccess.service;

import java.util.List;

/**
 * A table to export. Cells are numbers, text or null. Numbers stay numeric in Excel and are written
 * as plain values in CSV.
 */
public record Report(String name, List<String> headers, List<List<Object>> rows) {
}
