package com.eduaccess.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate queries for the impact dashboard. Dates are inclusive and compared as yyyy-MM-dd text. */
public final class ReportDAO {

    public int countDigitalDownloads(Connection c, LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM digital_downloads WHERE date(downloaded_at) BETWEEN ? AND ?")) {
            Jdbc.setDate(ps, 1, from);
            Jdbc.setDate(ps, 2, to);
            return singleInt(ps);
        }
    }

    /** Distinct people who borrowed something or downloaded something in the range. */
    public int countActiveUsers(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT borrower_id AS user_id FROM loans WHERE borrow_date BETWEEN ? AND ?
                    UNION
                    SELECT user_id FROM digital_downloads WHERE date(downloaded_at) BETWEEN ? AND ?
                )
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setDate(ps, 1, from);
            Jdbc.setDate(ps, 2, to);
            Jdbc.setDate(ps, 3, from);
            Jdbc.setDate(ps, 4, to);
            return singleInt(ps);
        }
    }

    /** Most downloaded subjects in the range, highest first. */
    public Map<String, Integer> topSubjects(Connection c, LocalDate from, LocalDate to, int limit)
            throws SQLException {
        String sql = """
                SELECT r.subject, COUNT(*) AS requests
                FROM digital_downloads d
                JOIN digital_resources r ON r.resource_id = d.resource_id
                WHERE date(d.downloaded_at) BETWEEN ? AND ?
                GROUP BY r.subject
                ORDER BY requests DESC, r.subject
                LIMIT ?
                """;
        Map<String, Integer> subjects = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setDate(ps, 1, from);
            Jdbc.setDate(ps, 2, to);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    subjects.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return subjects;
    }

    private static int singleInt(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
