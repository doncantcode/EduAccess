package com.eduaccess.dao;

import com.eduaccess.model.DbCoded;
import com.eduaccess.model.DonationDraft;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** One saved donation form per student. Saving again replaces it. */
public final class DonationDraftDAO {

    public void save(Connection c, long userId, DonationDraft d) throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO donation_drafts
                    (user_id, category, title, brand_model, condition, levels, dropoff_point, photo_path, saved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            Jdbc.setCode(ps, 2, d.category());
            Jdbc.setText(ps, 3, d.title());
            Jdbc.setText(ps, 4, d.brandModel());
            Jdbc.setCode(ps, 5, d.condition());
            Jdbc.setText(ps, 6, d.levels() == null || d.levels().isEmpty() ? null
                    : d.levels().stream().map(EducationLevel::dbValue).collect(Collectors.joining(",")));
            Jdbc.setText(ps, 7, d.dropoffPoint());
            Jdbc.setText(ps, 8, d.photoPath());
            ps.executeUpdate();
        }
    }

    public Optional<DonationDraft> find(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM donation_drafts WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String levels = rs.getString("levels");
                List<EducationLevel> list = levels == null ? List.of() : Arrays.stream(levels.split(","))
                        .map(value -> DbCoded.fromDbValue(EducationLevel.class, value))
                        .toList();
                return Optional.of(new DonationDraft(
                        Jdbc.code(rs, "category", ItemCategory.class),
                        rs.getString("title"),
                        rs.getString("brand_model"),
                        Jdbc.code(rs, "condition", ItemCondition.class),
                        list,
                        rs.getString("dropoff_point"),
                        rs.getString("photo_path"),
                        LocalDateTime.parse(rs.getString("saved_at").replace(' ', 'T'))));
            }
        }
    }

    public void delete(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM donation_drafts WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }
}
