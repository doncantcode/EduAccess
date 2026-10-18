package com.eduaccess.dao;

import com.eduaccess.model.Announcement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Campus announcements. */
public final class AnnouncementDAO {

    private static final String SELECT = """
            SELECT a.*, u.full_name AS posted_by_name
            FROM announcements a JOIN users u ON u.user_id = a.posted_by
            """;

    public long insert(Connection c, long postedBy, String title, String body, LocalDate postedOn,
                       LocalDate expiresOn) throws SQLException {
        String sql = "INSERT INTO announcements (posted_by, title, body, posted_on, expires_on) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, postedBy);
            ps.setString(2, title);
            ps.setString(3, body);
            Jdbc.setDate(ps, 4, postedOn);
            Jdbc.setDate(ps, 5, expiresOn);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Announcements visible on the given day, newest first. */
    public List<Announcement> visibleOn(Connection c, LocalDate day) throws SQLException {
        return query(c, SELECT + """
                 WHERE a.removed = 0 AND a.posted_on <= ? AND (a.expires_on IS NULL OR a.expires_on >= ?)
                 ORDER BY a.posted_on DESC, a.announcement_id DESC
                """, day.toString(), day.toString());
    }

    /** Every announcement, newest first, including removed and expired ones. */
    public List<Announcement> all(Connection c) throws SQLException {
        return query(c, SELECT + " ORDER BY a.announcement_id DESC");
    }

    public boolean remove(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE announcements SET removed = 1 WHERE announcement_id = ? AND removed = 0")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private List<Announcement> query(Connection c, String sql, String... params) throws SQLException {
        List<Announcement> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Announcement(
                            rs.getLong("announcement_id"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getString("posted_by_name"),
                            Jdbc.date(rs, "posted_on"),
                            Jdbc.date(rs, "expires_on"),
                            Jdbc.bool(rs, "removed")));
                }
            }
        }
        return list;
    }
}
