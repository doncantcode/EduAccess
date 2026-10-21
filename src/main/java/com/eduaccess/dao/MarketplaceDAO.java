package com.eduaccess.dao;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.MarketplaceCategory;
import com.eduaccess.model.PeerBorrow;
import com.eduaccess.model.PeerBorrowStatus;
import com.eduaccess.model.PeerMessage;
import com.eduaccess.model.StudentListing;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access object for the Student Marketplace (listings, peer borrows, and messages). */
public final class MarketplaceDAO {

    public record NewListing(
            long ownerId,
            String title,
            String description,
            MarketplaceCategory category,
            ItemCondition condition,
            EducationLevel educationLevel,
            BigDecimal dailyFee,
            String pickupPoint,
            String photoPath
    ) {}

    public long insertListing(Connection c, NewListing l) throws SQLException {
        String sql = """
                INSERT INTO marketplace_listings (owner_id, title, description, category, condition,
                    education_level, daily_fee, pickup_point, photo_path, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Active')
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, l.ownerId());
            ps.setString(2, l.title());
            ps.setString(3, l.description());
            Jdbc.setCode(ps, 4, l.category());
            Jdbc.setCode(ps, 5, l.condition());
            Jdbc.setCode(ps, 6, l.educationLevel());
            ps.setString(7, l.dailyFee().toPlainString());
            ps.setString(8, l.pickupPoint());
            Jdbc.setText(ps, 9, l.photoPath());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void updateListing(Connection c, long listingId, String title, String description,
                              MarketplaceCategory category, ItemCondition condition,
                              EducationLevel educationLevel, BigDecimal dailyFee,
                              String pickupPoint, String photoPath) throws SQLException {
        String sql = """
                UPDATE marketplace_listings
                SET title = ?, description = ?, category = ?, condition = ?, education_level = ?,
                    daily_fee = ?, pickup_point = ?, photo_path = COALESCE(?, photo_path),
                    updated_at = datetime('now')
                WHERE listing_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            Jdbc.setCode(ps, 3, category);
            Jdbc.setCode(ps, 4, condition);
            Jdbc.setCode(ps, 5, educationLevel);
            ps.setString(6, dailyFee.toPlainString());
            ps.setString(7, pickupPoint);
            Jdbc.setText(ps, 8, photoPath);
            ps.setLong(9, listingId);
            ps.executeUpdate();
        }
    }

    public void setListingStatus(Connection c, long listingId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE marketplace_listings SET status = ?, updated_at = datetime('now') WHERE listing_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, listingId);
            ps.executeUpdate();
        }
    }

    public Optional<StudentListing> findListing(Connection c, long listingId) throws SQLException {
        return queryListings(c, "SELECT * FROM marketplace_listings WHERE listing_id = ?", listingId).stream().findFirst();
    }

    public List<StudentListing> listingsByOwner(Connection c, long ownerId) throws SQLException {
        return queryListings(c, "SELECT * FROM marketplace_listings WHERE owner_id = ? ORDER BY listing_id DESC", ownerId);
    }

    public List<StudentListing> browseListings(Connection c, MarketplaceCategory category,
                                             EducationLevel level, String keyword) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT * FROM marketplace_listings WHERE status = 'Active'");
        List<Object> args = new ArrayList<>();
        if (category != null) {
            sb.append(" AND category = ?");
            args.add(category.dbValue());
        }
        if (level != null) {
            sb.append(" AND education_level = ?");
            args.add(level.dbValue());
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (title LIKE ? OR description LIKE ?)");
            String pattern = "%" + keyword.strip() + "%";
            args.add(pattern);
            args.add(pattern);
        }
        sb.append(" ORDER BY listing_id DESC");
        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentListing> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapListing(rs));
                }
                return list;
            }
        }
    }

    public long insertBorrow(Connection c, long listingId, long borrowerId, long ownerId,
                             BigDecimal agreedFee, String note) throws SQLException {
        String sql = """
                INSERT INTO marketplace_borrows (listing_id, borrower_id, owner_id, status, agreed_fee, note)
                VALUES (?, ?, ?, 'Requested', ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, listingId);
            ps.setLong(2, borrowerId);
            ps.setLong(3, ownerId);
            ps.setString(4, agreedFee.toPlainString());
            Jdbc.setText(ps, 5, note);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean moveBorrow(Connection c, long borrowId, PeerBorrowStatus from, PeerBorrowStatus to)
            throws SQLException {
        String timestampCol = switch (to) {
            case ACCEPTED, DECLINED -> ", responded_at = datetime('now')";
            case BORROWED -> ", borrowed_at = datetime('now')";
            case RETURNED -> ", returned_at = datetime('now')";
            default -> "";
        };
        String sql = "UPDATE marketplace_borrows SET status = ?" + timestampCol + " WHERE borrow_id = ? AND status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.dbValue());
            ps.setLong(2, borrowId);
            ps.setString(3, from.dbValue());
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<PeerBorrow> findBorrow(Connection c, long borrowId) throws SQLException {
        return queryBorrows(c, "SELECT * FROM marketplace_borrows WHERE borrow_id = ?", borrowId).stream().findFirst();
    }

    public List<PeerBorrow> borrowsByListing(Connection c, long listingId) throws SQLException {
        return queryBorrows(c, "SELECT * FROM marketplace_borrows WHERE listing_id = ? ORDER BY borrow_id DESC", listingId);
    }

    public List<PeerBorrow> borrowsByBorrower(Connection c, long borrowerId) throws SQLException {
        return queryBorrows(c, "SELECT * FROM marketplace_borrows WHERE borrower_id = ? ORDER BY borrow_id DESC", borrowerId);
    }

    public List<PeerBorrow> borrowsByOwner(Connection c, long ownerId) throws SQLException {
        return queryBorrows(c, "SELECT * FROM marketplace_borrows WHERE owner_id = ? ORDER BY borrow_id DESC", ownerId);
    }

    public long insertMessage(Connection c, long listingId, long senderId, String body) throws SQLException {
        String sql = "INSERT INTO marketplace_messages (listing_id, sender_id, body) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, listingId);
            ps.setLong(2, senderId);
            ps.setString(3, body);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<PeerMessage> messagesByListing(Connection c, long listingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM marketplace_messages WHERE listing_id = ? ORDER BY message_id ASC")) {
            ps.setLong(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PeerMessage> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new PeerMessage(
                            rs.getLong("message_id"),
                            rs.getLong("listing_id"),
                            rs.getLong("sender_id"),
                            rs.getString("body"),
                            rs.getString("sent_at")
                    ));
                }
                return list;
            }
        }
    }

    public List<StudentListing> conversationsForUser(Connection c, long userId) throws SQLException {
        String sql = """
                SELECT DISTINCT l.* FROM marketplace_listings l
                JOIN marketplace_messages m ON m.listing_id = l.listing_id
                WHERE l.owner_id = ? OR m.sender_id = ?
                ORDER BY l.listing_id DESC
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentListing> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapListing(rs));
                }
                return list;
            }
        }
    }

    private List<StudentListing> queryListings(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (param instanceof Long l) {
                ps.setLong(1, l);
            } else {
                ps.setString(1, (String) param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentListing> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapListing(rs));
                }
                return list;
            }
        }
    }

    private List<PeerBorrow> queryBorrows(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (param instanceof Long l) {
                ps.setLong(1, l);
            } else {
                ps.setString(1, (String) param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<PeerBorrow> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new PeerBorrow(
                            rs.getLong("borrow_id"),
                            rs.getLong("listing_id"),
                            rs.getLong("borrower_id"),
                            rs.getLong("owner_id"),
                            Jdbc.code(rs, "status", PeerBorrowStatus.class),
                            rs.getBigDecimal("agreed_fee"),
                            rs.getString("note"),
                            rs.getString("requested_at"),
                            rs.getString("responded_at"),
                            rs.getString("borrowed_at"),
                            rs.getString("returned_at")
                    ));
                }
                return list;
            }
        }
    }

    private StudentListing mapListing(ResultSet rs) throws SQLException {
        return new StudentListing(
                rs.getLong("listing_id"),
                rs.getLong("owner_id"),
                rs.getString("title"),
                rs.getString("description"),
                Jdbc.code(rs, "category", MarketplaceCategory.class),
                Jdbc.code(rs, "condition", ItemCondition.class),
                Jdbc.code(rs, "education_level", EducationLevel.class),
                rs.getBigDecimal("daily_fee"),
                rs.getString("pickup_point"),
                rs.getString("photo_path"),
                rs.getString("status"),
                rs.getString("created_at")
        );
    }
}
