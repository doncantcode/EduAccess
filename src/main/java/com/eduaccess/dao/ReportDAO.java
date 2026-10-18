package com.eduaccess.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
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

    /** Loans and downloads in the range per education level of the person: {physical loans, digital downloads}. */
    public Map<String, int[]> usageByLevel(Connection c, LocalDate from, LocalDate to) throws SQLException {
        Map<String, int[]> usage = new LinkedHashMap<>();
        for (String level : new String[] {"Elementary", "JHS", "SHS", "College"}) {
            usage.put(level, new int[2]);
        }
        String loans = """
                SELECT u.education_level, COUNT(*) FROM loans l JOIN users u ON u.user_id = l.borrower_id
                WHERE l.borrow_date BETWEEN ? AND ? AND u.education_level IS NOT NULL GROUP BY u.education_level
                """;
        String downloads = """
                SELECT u.education_level, COUNT(*) FROM digital_downloads d JOIN users u ON u.user_id = d.user_id
                WHERE date(d.downloaded_at) BETWEEN ? AND ? AND u.education_level IS NOT NULL
                GROUP BY u.education_level
                """;
        fill(c, loans, from, to, usage, 0);
        fill(c, downloads, from, to, usage, 1);
        return usage;
    }

    private static void fill(Connection c, String sql, LocalDate from, LocalDate to, Map<String, int[]> usage,
                             int slot) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setDate(ps, 1, from);
            Jdbc.setDate(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int[] counts = usage.get(rs.getString(1));
                    if (counts != null) {
                        counts[slot] = rs.getInt(2);
                    }
                }
            }
        }
    }

    /** One line of the recent-activity feed. */
    public record Activity(String when, String who, String what, String level, String kind) {
    }

    /**
     * The latest borrows, returns, downloads, donations and rental offers, newest first. Timestamps the database
     * stores in UTC are shown in local time.
     */
    public List<Activity> recentActivity(Connection c, int limit) throws SQLException {
        String sql = """
                SELECT * FROM (
                    SELECT l.borrow_date AS happened, u.full_name AS who, 'borrowed ' || i.title AS what,
                           u.education_level AS level, 'Borrowed' AS kind
                    FROM loans l JOIN users u ON u.user_id = l.borrower_id JOIN physical_items i ON i.item_id = l.item_id
                    UNION ALL
                    SELECT l.return_date, u.full_name, 'returned ' || i.title, u.education_level, 'Returned'
                    FROM loans l JOIN users u ON u.user_id = l.borrower_id JOIN physical_items i ON i.item_id = l.item_id
                    WHERE l.return_date IS NOT NULL
                    UNION ALL
                    SELECT datetime(d.downloaded_at, 'localtime'), u.full_name, 'downloaded ' || r.title, u.education_level, 'Download'
                    FROM digital_downloads d JOIN users u ON u.user_id = d.user_id
                    JOIN digital_resources r ON r.resource_id = d.resource_id
                    UNION ALL
                    SELECT datetime(i.created_at, 'localtime'), u.full_name, 'donated ' || i.title, u.education_level, 'Donated'
                    FROM physical_items i JOIN users u ON u.user_id = i.donor_id
                    UNION ALL
                    SELECT datetime(o.offered_at, 'localtime'), u.full_name, 'offered ' || o.item_name || ' for rent', u.education_level,
                           'Rental offer'
                    FROM rental_offers o JOIN users u ON u.user_id = o.owner_id
                )
                ORDER BY happened DESC
                LIMIT ?
                """;
        List<Activity> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Activity(rs.getString("happened"), rs.getString("who"), rs.getString("what"),
                            rs.getString("level"), rs.getString("kind")));
                }
            }
        }
        return list;
    }

    private static int singleInt(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
