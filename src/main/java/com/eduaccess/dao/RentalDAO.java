package com.eduaccess.dao;

import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.RentalRequestStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Library Rental Requests: what the library wants to rent, and the offers students make. */
public final class RentalDAO {

    private static final String SELECT_REQUESTS = """
            SELECT r.*,
                   (SELECT COUNT(*) FROM rental_offers o WHERE o.request_id = r.request_id
                        AND o.status IN ('Approved', 'Checked-In', 'Returned')) AS approved_count,
                   (SELECT COUNT(*) FROM rental_offers o WHERE o.request_id = r.request_id
                        AND o.status = 'Offered') AS pending_count
            FROM rental_requests r
            """;

    private static final String SELECT_OFFERS = """
            SELECT o.*, r.title AS request_title, u.full_name AS owner_name, u.school_id AS owner_school_id
            FROM rental_offers o
            JOIN rental_requests r ON r.request_id = o.request_id
            JOIN users u ON u.user_id = o.owner_id
            """;

    /** Values for a new request, already validated by the service. */
    public record NewRequest(
            long createdBy,
            String title,
            String description,
            String purpose,
            RentalCategory category,
            int quantityNeeded,
            ItemCondition minCondition,
            LocalDate offersCloseOn,
            LocalDate neededFrom,
            LocalDate neededUntil,
            BigDecimal rentalFee,
            String dropoffPoint) {
    }

    /** Values for a new offer, already validated by the service. */
    public record NewOffer(
            long requestId,
            long ownerId,
            String itemName,
            String brandModel,
            String description,
            ItemCondition condition,
            String photoPath,
            String ownerNote) {
    }

    public long insertRequest(Connection c, NewRequest r) throws SQLException {
        String sql = """
                INSERT INTO rental_requests (created_by, title, description, purpose, category, quantity_needed,
                    min_condition, offers_close_on, needed_from, needed_until, rental_fee, dropoff_point)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.createdBy());
            ps.setString(2, r.title());
            ps.setString(3, r.description());
            ps.setString(4, r.purpose());
            Jdbc.setCode(ps, 5, r.category());
            ps.setInt(6, r.quantityNeeded());
            Jdbc.setCode(ps, 7, r.minCondition());
            Jdbc.setDate(ps, 8, r.offersCloseOn());
            Jdbc.setDate(ps, 9, r.neededFrom());
            Jdbc.setDate(ps, 10, r.neededUntil());
            ps.setString(11, r.rentalFee().toPlainString());
            ps.setString(12, r.dropoffPoint());
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    public long insertOffer(Connection c, NewOffer o) throws SQLException {
        String sql = """
                INSERT INTO rental_offers (request_id, owner_id, item_name, brand_model, description, condition,
                    photo_path, owner_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, o.requestId());
            ps.setLong(2, o.ownerId());
            ps.setString(3, o.itemName());
            Jdbc.setText(ps, 4, o.brandModel());
            Jdbc.setText(ps, 5, o.description());
            Jdbc.setCode(ps, 6, o.condition());
            Jdbc.setText(ps, 7, o.photoPath());
            Jdbc.setText(ps, 8, o.ownerNote());
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    public Optional<RentalRequest> findRequest(Connection c, long requestId) throws SQLException {
        return requests(c, SELECT_REQUESTS + " WHERE r.request_id = ?", requestId).stream().findFirst();
    }

    /** Every request, newest first. */
    public List<RentalRequest> allRequests(Connection c) throws SQLException {
        return requests(c, SELECT_REQUESTS + " ORDER BY r.request_id DESC");
    }

    public List<RentalRequest> requestsWithStatus(Connection c, RentalRequestStatus status) throws SQLException {
        return requests(c, SELECT_REQUESTS + " WHERE r.status = ? ORDER BY r.offers_close_on, r.request_id",
                status.dbValue());
    }

    public Optional<RentalOffer> findOffer(Connection c, long offerId) throws SQLException {
        return offers(c, SELECT_OFFERS + " WHERE o.offer_id = ?", offerId).stream().findFirst();
    }

    public List<RentalOffer> offersForRequest(Connection c, long requestId) throws SQLException {
        return offers(c, SELECT_OFFERS + " WHERE o.request_id = ? ORDER BY o.offer_id", requestId);
    }

    public List<RentalOffer> offersByOwner(Connection c, long ownerId) throws SQLException {
        return offers(c, SELECT_OFFERS + " WHERE o.owner_id = ? ORDER BY o.offer_id DESC", ownerId);
    }

    public int countOffersWithStatus(Connection c, OfferStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM rental_offers WHERE status = ?")) {
            ps.setString(1, status.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Changes a request's status only if it is still {@code from}. Returns false otherwise. */
    public boolean moveRequest(Connection c, long requestId, RentalRequestStatus from, RentalRequestStatus to)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE rental_requests SET status = ? WHERE request_id = ? AND status = ?")) {
            ps.setString(1, to.dbValue());
            ps.setLong(2, requestId);
            ps.setString(3, from.dbValue());
            return ps.executeUpdate() == 1;
        }
    }

    /** Staff decision on an Offered offer. agreedFee is set on approval and null on decline. */
    public boolean decide(Connection c, long offerId, OfferStatus to, long staffId, BigDecimal agreedFee,
                          String declineReason) throws SQLException {
        String sql = """
                UPDATE rental_offers SET status = ?, decided_by = ?, agreed_fee = ?, decline_reason = ?,
                    decided_at = datetime('now')
                WHERE offer_id = ? AND status = 'Offered'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.dbValue());
            ps.setLong(2, staffId);
            Jdbc.setText(ps, 3, agreedFee == null ? null : agreedFee.toPlainString());
            Jdbc.setText(ps, 4, declineReason);
            ps.setLong(5, offerId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Declines every offer on the request that is still waiting. Returns how many changed. */
    public int declineWaitingOffers(Connection c, long requestId, String reason) throws SQLException {
        String sql = """
                UPDATE rental_offers SET status = 'Declined', decline_reason = ?, decided_at = datetime('now')
                WHERE request_id = ? AND status = 'Offered'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setLong(2, requestId);
            return ps.executeUpdate();
        }
    }

    /** Cancels approved offers that have not been handed over yet. Returns how many changed. */
    public int cancelApprovedOffers(Connection c, long requestId, String reason) throws SQLException {
        String sql = """
                UPDATE rental_offers SET status = 'Cancelled', decline_reason = ?
                WHERE request_id = ? AND status = 'Approved'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setLong(2, requestId);
            return ps.executeUpdate();
        }
    }

    public boolean withdraw(Connection c, long offerId, OfferStatus from) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE rental_offers SET status = 'Withdrawn' WHERE offer_id = ? AND status = ?")) {
            ps.setLong(1, offerId);
            ps.setString(2, from.dbValue());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean checkIn(Connection c, long offerId, ItemCondition condition) throws SQLException {
        String sql = """
                UPDATE rental_offers SET status = 'Checked-In', condition_in = ?, checked_in_at = datetime('now')
                WHERE offer_id = ? AND status = 'Approved'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setCode(ps, 1, condition);
            ps.setLong(2, offerId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean returnToOwner(Connection c, long offerId, ItemCondition condition, String damageNote,
                                 boolean feePaid) throws SQLException {
        String sql = """
                UPDATE rental_offers SET status = 'Returned', condition_out = ?, damage_note = ?, fee_paid = ?,
                    returned_at = datetime('now')
                WHERE offer_id = ? AND status = 'Checked-In'
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setCode(ps, 1, condition);
            Jdbc.setText(ps, 2, damageNote);
            ps.setInt(3, feePaid ? 1 : 0);
            ps.setLong(4, offerId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Offers on the request that are Approved or Checked-In, the ones the library still has to deal with. */
    public int countOutstanding(Connection c, long requestId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM rental_offers WHERE request_id = ? AND status IN ('Approved', 'Checked-In')
                """)) {
            ps.setLong(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<RentalRequest> requests(Connection c, String sql, Object... params) throws SQLException {
        List<RentalRequest> list = new ArrayList<>();
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new RentalRequest(
                        rs.getLong("request_id"),
                        rs.getLong("created_by"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("purpose"),
                        Jdbc.code(rs, "category", RentalCategory.class),
                        rs.getInt("quantity_needed"),
                        Jdbc.code(rs, "min_condition", ItemCondition.class),
                        Jdbc.date(rs, "offers_close_on"),
                        Jdbc.date(rs, "needed_from"),
                        Jdbc.date(rs, "needed_until"),
                        Jdbc.money(rs, "rental_fee"),
                        rs.getString("dropoff_point"),
                        Jdbc.code(rs, "status", RentalRequestStatus.class),
                        rs.getInt("approved_count"),
                        rs.getInt("pending_count")));
            }
        }
        return list;
    }

    private List<RentalOffer> offers(Connection c, String sql, Object... params) throws SQLException {
        List<RentalOffer> list = new ArrayList<>();
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String fee = rs.getString("agreed_fee");
                list.add(new RentalOffer(
                        rs.getLong("offer_id"),
                        rs.getLong("request_id"),
                        rs.getString("request_title"),
                        rs.getLong("owner_id"),
                        rs.getString("owner_name"),
                        rs.getString("owner_school_id"),
                        rs.getString("item_name"),
                        rs.getString("brand_model"),
                        rs.getString("description"),
                        Jdbc.code(rs, "condition", ItemCondition.class),
                        rs.getString("photo_path"),
                        rs.getString("owner_note"),
                        Jdbc.code(rs, "status", OfferStatus.class),
                        fee == null ? null : Jdbc.money(rs, "agreed_fee"),
                        Jdbc.bool(rs, "fee_paid"),
                        rs.getString("decline_reason"),
                        Jdbc.code(rs, "condition_in", ItemCondition.class),
                        Jdbc.code(rs, "condition_out", ItemCondition.class),
                        rs.getString("damage_note")));
            }
        }
        return list;
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Long id) {
                ps.setLong(i + 1, id);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
        return ps;
    }

    private static long generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}
