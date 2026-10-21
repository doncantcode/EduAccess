package com.eduaccess.dao;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.NewUser;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.security.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Accounts. Every method runs on a connection the caller provides, so callers can group writes in a transaction. */
public final class UserDAO {

    private static final String PROFILE_COLUMNS =
            "user_id, school_id, full_name, role, intention, education_level, program, year_level, campus";

    public long registerUser(Connection c, NewUser user) throws SQLException {
        String sql = """
                INSERT INTO users (school_id, password_hash, full_name, role, intention,
                                   education_level, program, year_level, campus)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Jdbc.setText(ps, 1, user.schoolId());
            Jdbc.setText(ps, 2, user.passwordHash());
            Jdbc.setText(ps, 3, user.fullName());
            Jdbc.setCode(ps, 4, user.role());
            Jdbc.setCode(ps, 5, user.intention());
            Jdbc.setCode(ps, 6, user.educationLevel());
            Jdbc.setText(ps, 7, user.program());
            Jdbc.setInt(ps, 8, user.yearLevel());
            Jdbc.setText(ps, 9, user.campus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<User> findBySchoolId(Connection c, String schoolId) throws SQLException {
        return queryOne(c, "SELECT " + PROFILE_COLUMNS + " FROM users WHERE school_id = ?", schoolId);
    }

    public Optional<User> getProfile(Connection c, long userId) throws SQLException {
        return queryOne(c, "SELECT " + PROFILE_COLUMNS + " FROM users WHERE user_id = ?", userId);
    }

    /** Returns the profile when the school ID exists and the password matches. */
    public Optional<User> authenticate(Connection c, String schoolId, char[] password) throws SQLException {
        String sql = "SELECT password_hash, " + PROFILE_COLUMNS + " FROM users WHERE school_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schoolId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordHasher.verify(password, rs.getString("password_hash"))) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    private Optional<User> queryOne(Connection c, String sql, Object key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (key instanceof Long id) {
                ps.setLong(1, id);
            } else {
                ps.setString(1, (String) key);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("user_id"),
                rs.getString("school_id"),
                rs.getString("full_name"),
                Jdbc.code(rs, "role", Role.class),
                Jdbc.code(rs, "intention", Intention.class),
                Jdbc.code(rs, "education_level", EducationLevel.class),
                rs.getString("program"),
                Jdbc.nullableInt(rs, "year_level"),
                rs.getString("campus"));
    }

    /** Records a guardian's consent for a learner under 18. The row also marks the account as a minor. */
    public void insertGuardianConsent(Connection c, long userId, String guardianName, String relationship,
                                      String contactNumber) throws SQLException {
        String sql = """
                INSERT INTO guardian_consents (user_id, guardian_name, relationship, contact_number)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, guardianName);
            ps.setString(3, relationship);
            ps.setString(4, contactNumber);
            ps.executeUpdate();
        }
    }

    /** True when the account has a guardian consent on file, which means the student is under 18. */
    public boolean isMinor(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM guardian_consents WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Updates a user's password directly. */
    public void updatePassword(Connection c, long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void insertPasswordResetRequest(Connection c, long userId) throws SQLException {
        String sql = "INSERT INTO password_reset_requests (user_id, status) VALUES (?, 'Pending')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public java.util.List<com.eduaccess.model.PasswordResetRequest> pendingPasswordResetRequests(Connection c) throws SQLException {
        String sql = """
                SELECT r.request_id, r.user_id, u.school_id, u.full_name, r.requested_at, r.status
                FROM password_reset_requests r
                JOIN users u ON u.user_id = r.user_id
                WHERE r.status = 'Pending'
                ORDER BY r.request_id ASC
                """;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            java.util.List<com.eduaccess.model.PasswordResetRequest> list = new java.util.ArrayList<>();
            while (rs.next()) {
                list.add(new com.eduaccess.model.PasswordResetRequest(
                        rs.getLong("request_id"),
                        rs.getLong("user_id"),
                        rs.getString("school_id"),
                        rs.getString("full_name"),
                        rs.getString("requested_at"),
                        rs.getString("status")
                ));
            }
            return list;
        }
    }

    public void resolvePasswordResetRequest(Connection c, long requestId, long adminId) throws SQLException {
        String sql = "UPDATE password_reset_requests SET status = 'Approved', resolved_by = ?, resolved_at = datetime('now') WHERE request_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.setLong(2, requestId);
            ps.executeUpdate();
        }
    }

    public void rejectPasswordResetRequest(Connection c, long requestId, long adminId) throws SQLException {
        String sql = "UPDATE password_reset_requests SET status = 'Rejected', resolved_by = ?, resolved_at = datetime('now') WHERE request_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.setLong(2, requestId);
            ps.executeUpdate();
        }
    }

    public java.util.Optional<com.eduaccess.model.PasswordResetRequest> findPasswordResetRequest(Connection c, long requestId) throws SQLException {
        String sql = """
                SELECT r.request_id, r.user_id, u.school_id, u.full_name, r.requested_at, r.status
                FROM password_reset_requests r
                JOIN users u ON u.user_id = r.user_id
                WHERE r.request_id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(new com.eduaccess.model.PasswordResetRequest(
                            rs.getLong("request_id"),
                            rs.getLong("user_id"),
                            rs.getString("school_id"),
                            rs.getString("full_name"),
                            rs.getString("requested_at"),
                            rs.getString("status")
                    ));
                }
                return java.util.Optional.empty();
            }
        }
    }
}
