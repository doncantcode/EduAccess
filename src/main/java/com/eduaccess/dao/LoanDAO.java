package com.eduaccess.dao;

import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
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

/** Physical loans. Every method runs on the caller's connection, so services can group writes in one transaction. */
public final class LoanDAO {

    private static final String DETAIL_SELECT = """
            SELECT l.loan_id, l.borrower_id, l.item_id, l.borrow_date, l.due_date, l.return_date,
                   l.loan_period_days, l.extension_count, l.fee_amount, l.fee_waived, l.status,
                   i.title AS item_title, u.full_name AS borrower_name, u.school_id AS borrower_school_id
            FROM loans l
            JOIN physical_items i ON i.item_id = l.item_id
            JOIN users u ON u.user_id = l.borrower_id
            """;

    public long insert(Connection c, Loan loan) throws SQLException {
        String sql = """
                INSERT INTO loans (borrower_id, item_id, borrow_date, due_date, return_date,
                                   loan_period_days, extension_count, fee_amount, fee_waived, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, loan.borrowerId());
            ps.setLong(2, loan.itemId());
            Jdbc.setDate(ps, 3, loan.borrowDate());
            Jdbc.setDate(ps, 4, loan.dueDate());
            Jdbc.setDate(ps, 5, loan.returnDate());
            ps.setInt(6, loan.loanPeriodDays());
            ps.setInt(7, loan.extensionCount());
            ps.setBigDecimal(8, loan.feeAmount());
            ps.setInt(9, loan.feeWaived() ? 1 : 0);
            Jdbc.setCode(ps, 10, loan.status());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Loan> findById(Connection c, long loanId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM loans WHERE loan_id = ?")) {
            ps.setLong(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapLoan(rs)) : Optional.empty();
            }
        }
    }

    /** Open loans (Active or Overdue) of one borrower, earliest due date first. */
    public List<LoanDetail> getActiveLoansByUser(Connection c, long userId) throws SQLException {
        return queryDetails(c, DETAIL_SELECT + " WHERE l.borrower_id = ? AND l.status <> 'Returned'"
                + " ORDER BY l.due_date", List.of(userId));
    }

    public List<LoanDetail> getOverdueLoans(Connection c) throws SQLException {
        return queryDetails(c, DETAIL_SELECT + " WHERE l.status = 'Overdue' ORDER BY l.due_date", List.of());
    }

    /** Every loan, newest first. Used for the borrowing log. */
    public List<LoanDetail> listAll(Connection c) throws SQLException {
        return queryDetails(c, DETAIL_SELECT + " ORDER BY l.borrow_date DESC, l.loan_id DESC", List.of());
    }

    /** Loans that are not yet returned, used to refresh overdue status and fees. */
    public List<Loan> findOpenLoans(Connection c) throws SQLException {
        List<Loan> loans = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM loans WHERE status <> 'Returned' ORDER BY loan_id")) {
            while (rs.next()) {
                loans.add(mapLoan(rs));
            }
        }
        return loans;
    }

    public void updateLoanStatus(Connection c, long loanId, LoanStatus status, LocalDate returnDate,
                                 BigDecimal feeAmount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loans SET status = ?, return_date = ?, fee_amount = ? WHERE loan_id = ?")) {
            Jdbc.setCode(ps, 1, status);
            Jdbc.setDate(ps, 2, returnDate);
            ps.setBigDecimal(3, feeAmount);
            ps.setLong(4, loanId);
            ps.executeUpdate();
        }
    }

    public void extendLoan(Connection c, long loanId, LocalDate newDueDate, int extensionCount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loans SET due_date = ?, extension_count = ? WHERE loan_id = ?")) {
            Jdbc.setDate(ps, 1, newDueDate);
            ps.setInt(2, extensionCount);
            ps.setLong(3, loanId);
            ps.executeUpdate();
        }
    }

    public void waiveFee(Connection c, long loanId, boolean waived, BigDecimal feeAmount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loans SET fee_waived = ?, fee_amount = ? WHERE loan_id = ?")) {
            ps.setInt(1, waived ? 1 : 0);
            ps.setBigDecimal(2, feeAmount);
            ps.setLong(3, loanId);
            ps.executeUpdate();
        }
    }

    public void updateFee(Connection c, long loanId, BigDecimal feeAmount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE loans SET fee_amount = ? WHERE loan_id = ?")) {
            ps.setBigDecimal(1, feeAmount);
            ps.setLong(2, loanId);
            ps.executeUpdate();
        }
    }

    public int countOpenByUser(Connection c, long userId) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM loans WHERE borrower_id = ? AND status <> 'Returned'", userId);
    }

    /** Open loans that are still on time and fall due between {@code from} and {@code to}, inclusive. */
    public int countDueBetween(Connection c, long userId, LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM loans
                WHERE borrower_id = ? AND status = 'Active' AND due_date BETWEEN ? AND ?
                """)) {
            ps.setLong(1, userId);
            Jdbc.setDate(ps, 2, from);
            Jdbc.setDate(ps, 3, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int countOverdue(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM loans WHERE status = 'Overdue'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countAll(Connection c, LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE borrow_date BETWEEN ? AND ?")) {
            Jdbc.setDate(ps, 1, from);
            Jdbc.setDate(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int scalar(Connection c, String sql, long param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<LoanDetail> queryDetails(Connection c, String sql, List<Long> params) throws SQLException {
        List<LoanDetail> details = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setLong(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    details.add(new LoanDetail(
                            mapLoan(rs),
                            rs.getString("item_title"),
                            rs.getString("borrower_name"),
                            rs.getString("borrower_school_id")));
                }
            }
        }
        return details;
    }

    static Loan mapLoan(ResultSet rs) throws SQLException {
        return new Loan(
                rs.getLong("loan_id"),
                rs.getLong("borrower_id"),
                rs.getLong("item_id"),
                Jdbc.date(rs, "borrow_date"),
                Jdbc.date(rs, "due_date"),
                Jdbc.date(rs, "return_date"),
                rs.getInt("loan_period_days"),
                rs.getInt("extension_count"),
                Jdbc.money(rs, "fee_amount"),
                Jdbc.bool(rs, "fee_waived"),
                Jdbc.code(rs, "status", LoanStatus.class));
    }
}
