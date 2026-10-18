package com.eduaccess.service;

import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Borrow, return, extend and fee rules for physical loans. Each operation runs in one transaction, so the
 * loan row and the item status change together or not at all.
 */
public final class LoanService {

    private static final BigDecimal ZERO_FEE = BigDecimal.ZERO.setScale(2);

    private final DatabaseManager db;
    private final LoanDAO loans;
    private final PhysicalItemDAO items;
    private final LoanRules rules;

    public LoanService(DatabaseManager db, LoanDAO loans, PhysicalItemDAO items, LoanRules rules) {
        this.db = db;
        this.loans = loans;
        this.items = items;
        this.rules = rules;
    }

    public LoanRules rules() {
        return rules;
    }

    /** Borrows an available item. The due date is the borrow date plus the chosen period. */
    public Loan borrow(User borrower, long itemId, int periodDays, LocalDate today) {
        LocalDate due = dueDateOrRefuse(today, periodDays);
        return Sql.write(db, c -> {
            PhysicalItem item = items.findById(c, itemId)
                    .orElseThrow(() -> new ServiceException("This item no longer exists."));
            int limit = rules.policy().maxOpenLoans();
            if (loans.countOpenByUser(c, borrower.id()) >= limit) {
                throw new ServiceException("You can borrow up to " + limit + " items at a time. "
                        + "Return one of your current loans first.");
            }
            if (item.status() != ItemStatus.AVAILABLE) {
                throw new ServiceException("This item is " + item.status().dbValue().toLowerCase()
                        + " and cannot be borrowed right now.");
            }
            if (!items.updateStatus(c, itemId, ItemStatus.AVAILABLE, ItemStatus.BORROWED)) {
                throw new ServiceException("Someone just borrowed this item. Please choose another.");
            }
            Loan draft = new Loan(0, borrower.id(), itemId, today, due, null, periodDays, 0, ZERO_FEE, false,
                    LoanStatus.ACTIVE);
            long id = loans.insert(c, draft);
            return new Loan(id, borrower.id(), itemId, today, due, null, periodDays, 0, ZERO_FEE, false,
                    LoanStatus.ACTIVE);
        });
    }

    /** The borrower returns the item. The fee is fixed at the return date. The item waits for a counter check. */
    public Loan returnItem(long loanId, LocalDate today) {
        return Sql.write(db, c -> {
            Loan loan = findOpen(c, loanId);
            BigDecimal fee = loan.feeWaived() ? ZERO_FEE : rules.overdueFee(loan.dueDate(), today);
            loans.updateLoanStatus(c, loanId, LoanStatus.RETURNED, today, fee);
            items.updateStatus(c, loan.itemId(), null, ItemStatus.RETURNED);
            return new Loan(loan.id(), loan.borrowerId(), loan.itemId(), loan.borrowDate(), loan.dueDate(),
                    today, loan.loanPeriodDays(), loan.extensionCount(), fee, loan.feeWaived(),
                    LoanStatus.RETURNED);
        });
    }

    /** Adds one extension of the policy's length. Only allowed while the loan is still on time. */
    public LocalDate extend(long loanId, LocalDate today) {
        return Sql.write(db, c -> {
            Loan loan = findOpen(c, loanId);
            if (!rules.canExtend(today, loan.dueDate(), loan.extensionCount())) {
                if (today.isAfter(loan.dueDate())) {
                    throw new ServiceException("This loan is overdue, so it can no longer be extended.");
                }
                throw new ServiceException("This loan has already used its extension.");
            }
            LocalDate newDue = rules.extendDueDate(today, loan.dueDate(), loan.extensionCount());
            loans.extendLoan(c, loanId, newDue, loan.extensionCount() + 1);
            return newDue;
        });
    }

    /**
     * Admin toggle for equity-program students. Waiving clears the fee balance to zero.
     * Removing the waiver recalculates the fee from the dates.
     */
    public void setFeeWaived(long loanId, boolean waived, LocalDate today) {
        Sql.write(db, c -> {
            Loan loan = loans.findById(c, loanId)
                    .orElseThrow(() -> new ServiceException("This loan no longer exists."));
            BigDecimal fee = ZERO_FEE;
            if (!waived) {
                LocalDate asOf = loan.status() == LoanStatus.RETURNED ? loan.returnDate() : today;
                fee = rules.overdueFee(loan.dueDate(), asOf);
            }
            loans.waiveFee(c, loanId, waived, fee);
            return null;
        });
    }

    /**
     * Brings every open loan in line with today's date. Loans past their due date become Overdue, the item
     * becomes Overdue, and the accrued fee is recalculated. Returns how many loans changed status.
     */
    public int refreshOverdue(LocalDate today) {
        return Sql.write(db, c -> {
            int changed = 0;
            for (Loan loan : loans.findOpenLoans(c)) {
                LoanStatus target = rules.statusOn(loan.dueDate(), today, false);
                BigDecimal fee = loan.feeWaived() || target == LoanStatus.ACTIVE
                        ? ZERO_FEE
                        : rules.overdueFee(loan.dueDate(), today);
                if (target != loan.status()) {
                    loans.updateLoanStatus(c, loan.id(), target, null, fee);
                    if (target == LoanStatus.OVERDUE) {
                        items.updateStatus(c, loan.itemId(), ItemStatus.BORROWED, ItemStatus.OVERDUE);
                    }
                    changed++;
                } else if (fee.compareTo(loan.feeAmount()) != 0) {
                    loans.updateFee(c, loan.id(), fee);
                }
            }
            return changed;
        });
    }

    /** Every loan of the user, open and returned, newest first. */
    public List<LoanDetail> history(User user, LocalDate today) {
        refreshOverdue(today);
        return Sql.read(db, c -> loans.getLoanHistoryByUser(c, user.id()));
    }

    public List<LoanDetail> myLoans(User user, LocalDate today) {
        refreshOverdue(today);
        return Sql.read(db, c -> loans.getActiveLoansByUser(c, user.id()));
    }

    public List<LoanDetail> overdueLoans(LocalDate today) {
        refreshOverdue(today);
        return Sql.read(db, loans::getOverdueLoans);
    }

    public List<LoanDetail> allLoans(LocalDate today) {
        refreshOverdue(today);
        return Sql.read(db, loans::listAll);
    }

    private LocalDate dueDateOrRefuse(LocalDate today, int periodDays) {
        try {
            return rules.dueDate(today, periodDays);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Choose a loan period of 3, 7 or 14 days.");
        }
    }

    private Loan findOpen(Connection c, long loanId) throws SQLException {
        Loan loan = loans.findById(c, loanId).orElseThrow(() -> new ServiceException("This loan no longer exists."));
        if (loan.status() == LoanStatus.RETURNED) {
            throw new ServiceException("This loan has already been returned.");
        }
        return loan;
    }
}
