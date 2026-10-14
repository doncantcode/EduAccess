package com.eduaccess.dao;

import com.eduaccess.model.DigitalResource;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.NewResource;
import com.eduaccess.model.ResourceTab;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Local index of digital PDFs, plus the download log. */
public final class DigitalResourceDAO {

    private static final String SELECT_RESOURCES = """
            SELECT resource_id, title, category_tab, subject, education_level, file_path,
                   file_size_mb, is_cached_offline, source, license
            FROM digital_resources
            """;

    /** Resources in one tab, optionally narrowed to one education level. */
    public List<DigitalResource> getResourcesByLevel(Connection c, ResourceTab tab, EducationLevel level)
            throws SQLException {
        return searchResources(c, tab, level, null);
    }

    /** Resources in one tab matching a level and a free-text search on title or subject. */
    public List<DigitalResource> searchResources(Connection c, ResourceTab tab, EducationLevel level, String text)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_RESOURCES + " WHERE category_tab = ?");
        List<Object> params = new ArrayList<>();
        params.add(tab.dbValue());
        if (level != null) {
            sql.append(" AND education_level = ?");
            params.add(level.dbValue());
        }
        if (text != null && !text.isBlank()) {
            sql.append(" AND (title LIKE ? OR subject LIKE ?)");
            String like = "%" + text.strip() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY title");
        return query(c, sql.toString(), params);
    }

    public Optional<DigitalResource> findById(Connection c, long resourceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT_RESOURCES + " WHERE resource_id = ?")) {
            ps.setLong(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<DigitalResource> findCached(Connection c) throws SQLException {
        return query(c, SELECT_RESOURCES + " WHERE is_cached_offline = 1 ORDER BY title", List.of());
    }

    /** Adds a PDF to the index. The file must already be in place at filePath. Admin-added files are local, so cached. */
    public long insert(Connection c, NewResource resource) throws SQLException {
        String sql = """
                INSERT INTO digital_resources (title, category_tab, subject, education_level, file_path,
                                               file_size_mb, is_cached_offline, source, license)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Jdbc.setText(ps, 1, resource.title());
            Jdbc.setCode(ps, 2, resource.tab());
            Jdbc.setText(ps, 3, resource.subject());
            Jdbc.setCode(ps, 4, resource.level());
            Jdbc.setText(ps, 5, resource.filePath());
            ps.setDouble(6, resource.fileSizeMb());
            Jdbc.setText(ps, 7, resource.source());
            Jdbc.setText(ps, 8, resource.license());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void toggleOfflineCache(Connection c, long resourceId, boolean cached) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE digital_resources SET is_cached_offline = ? WHERE resource_id = ?")) {
            ps.setInt(1, cached ? 1 : 0);
            ps.setLong(2, resourceId);
            ps.executeUpdate();
        }
    }

    public long recordDownload(Connection c, long userId, long resourceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO digital_downloads (user_id, resource_id) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, resourceId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Distinct modules a user has downloaded. Used for the dashboard count. */
    public int countDistinctDownloadsByUser(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(DISTINCT resource_id) FROM digital_downloads WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<DigitalResource> query(Connection c, String sql, List<Object> params) throws SQLException {
        List<DigitalResource> resources = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resources.add(map(rs));
                }
            }
        }
        return resources;
    }

    private static DigitalResource map(ResultSet rs) throws SQLException {
        return new DigitalResource(
                rs.getLong("resource_id"),
                rs.getString("title"),
                Jdbc.code(rs, "category_tab", ResourceTab.class),
                rs.getString("subject"),
                Jdbc.code(rs, "education_level", EducationLevel.class),
                rs.getString("file_path"),
                rs.getDouble("file_size_mb"),
                Jdbc.bool(rs, "is_cached_offline"),
                rs.getString("source"),
                rs.getString("license"));
    }
}
