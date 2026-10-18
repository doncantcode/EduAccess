package com.eduaccess.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs an export query and returns its column labels and rows. The SQL's column aliases become the spreadsheet
 * headers, so each report is one readable query. Parameters bind in order. Numbers stay numbers.
 */
public final class ExportDAO {

    /** A query result: headers from the column labels, then the rows. */
    public record Table(List<String> headers, List<List<Object>> rows) {
    }

    public Table run(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> headers = new ArrayList<>();
                for (int col = 1; col <= meta.getColumnCount(); col++) {
                    headers.add(meta.getColumnLabel(col));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(headers.size());
                    for (int col = 1; col <= headers.size(); col++) {
                        row.add(rs.getObject(col));
                    }
                    rows.add(row);
                }
                return new Table(headers, rows);
            }
        }
    }
}
