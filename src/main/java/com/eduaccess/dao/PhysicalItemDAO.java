package com.eduaccess.dao;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.NewItem;
import com.eduaccess.model.PhysicalItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Physical catalog: donations, library stock and their status. */
public final class PhysicalItemDAO {

    private static final String SELECT_ITEMS = """
            SELECT i.item_id, i.donor_id, d.full_name AS donor_name, i.title, i.brand_model, i.category,
                   i.condition, i.pickup_point, i.photo_path, i.status, i.type_of_giving
            FROM physical_items i
            LEFT JOIN users d ON d.user_id = i.donor_id
            """;

    public List<PhysicalItem> getAllItems(Connection c) throws SQLException {
        return query(c, SELECT_ITEMS + " ORDER BY i.item_id", List.of());
    }

    public List<PhysicalItem> filterItems(Connection c, ItemFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_ITEMS + " WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (!filter.categories().isEmpty()) {
            String marks = String.join(",", Collections.nCopies(filter.categories().size(), "?"));
            sql.append(" AND i.category IN (").append(marks).append(")");
            filter.categories().forEach(category -> params.add(category.dbValue()));
        }
        if (filter.level() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM physical_item_levels l"
                    + " WHERE l.item_id = i.item_id AND l.education_level = ?)");
            params.add(filter.level().dbValue());
        }
        if (filter.status() != null) {
            sql.append(" AND i.status = ?");
            params.add(filter.status().dbValue());
        } else {
            sql.append(" AND i.status NOT IN ('Pending', 'Rejected')");
        }
        if (filter.search() != null) {
            sql.append(" AND (i.title LIKE ? OR i.brand_model LIKE ?)");
            String like = "%" + filter.search() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY i.item_id");
        return query(c, sql.toString(), params);
    }

    public Optional<PhysicalItem> findById(Connection c, long itemId) throws SQLException {
        return query(c, SELECT_ITEMS + " WHERE i.item_id = ?", List.of(itemId)).stream().findFirst();
    }

    public List<PhysicalItem> getPendingApprovals(Connection c) throws SQLException {
        return query(c, SELECT_ITEMS + " WHERE i.status = 'Pending' ORDER BY i.item_id", List.of());
    }

    public long insertItem(Connection c, NewItem item) throws SQLException {
        String sql = """
                INSERT INTO physical_items (donor_id, title, brand_model, category, condition,
                                            pickup_point, photo_path, status, type_of_giving)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        long itemId;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Jdbc.setLong(ps, 1, item.donorId());
            Jdbc.setText(ps, 2, item.title());
            Jdbc.setText(ps, 3, item.brandModel());
            Jdbc.setCode(ps, 4, item.category());
            Jdbc.setCode(ps, 5, item.condition());
            Jdbc.setText(ps, 6, item.pickupPoint());
            Jdbc.setText(ps, 7, item.photoPath());
            Jdbc.setCode(ps, 8, item.status());
            Jdbc.setCode(ps, 9, item.givingType());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                itemId = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO physical_item_levels (item_id, education_level) VALUES (?, ?)")) {
            for (EducationLevel level : item.levels()) {
                ps.setLong(1, itemId);
                ps.setString(2, level.dbValue());
                ps.executeUpdate();
            }
        }
        return itemId;
    }

    /**
     * Changes the status only if it still equals {@code from}. Pass {@code from = null} to change it unconditionally.
     * Returns false when the item was in another state, so the caller can refuse the action.
     */
    public boolean updateStatus(Connection c, long itemId, ItemStatus from, ItemStatus to) throws SQLException {
        String sql = from == null
                ? "UPDATE physical_items SET status = ? WHERE item_id = ?"
                : "UPDATE physical_items SET status = ? WHERE item_id = ? AND status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.dbValue());
            ps.setLong(2, itemId);
            if (from != null) {
                ps.setString(3, from.dbValue());
            }
            return ps.executeUpdate() == 1;
        }
    }

    public int countAll(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM physical_items")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countByStatus(Connection c, ItemStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM physical_items WHERE status = ?")) {
            ps.setString(1, status.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int countDonatedBy(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM physical_items WHERE donor_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<PhysicalItem> query(Connection c, String sql, List<?> params) throws SQLException {
        Map<Long, List<EducationLevel>> levels = levelsByItem(c);
        List<PhysicalItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                if (value instanceof Long id) {
                    ps.setLong(i + 1, id);
                } else {
                    ps.setString(i + 1, value.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("item_id");
                    items.add(new PhysicalItem(
                            id,
                            Jdbc.nullableLong(rs, "donor_id"),
                            rs.getString("donor_name"),
                            rs.getString("title"),
                            rs.getString("brand_model"),
                            Jdbc.code(rs, "category", ItemCategory.class),
                            Jdbc.code(rs, "condition", ItemCondition.class),
                            rs.getString("pickup_point"),
                            rs.getString("photo_path"),
                            Jdbc.code(rs, "status", ItemStatus.class),
                            Jdbc.code(rs, "type_of_giving", GivingType.class),
                            levels.getOrDefault(id, List.of())));
                }
            }
        }
        return items;
    }

    private Map<Long, List<EducationLevel>> levelsByItem(Connection c) throws SQLException {
        Map<Long, List<EducationLevel>> byItem = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_id, education_level FROM physical_item_levels")) {
            while (rs.next()) {
                byItem.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>())
                        .add(Jdbc.code(rs, "education_level", EducationLevel.class));
            }
        }
        return byItem;
    }
}
