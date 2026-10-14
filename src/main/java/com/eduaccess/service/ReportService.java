package com.eduaccess.service;

import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.ReportDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dashboard counts and impact metrics, plus the rows used by the CSV and Excel exports. */
public final class ReportService {

    private static final int TOP_SUBJECTS = 6;

    private final DatabaseManager db;
    private final LoanDAO loans;
    private final PhysicalItemDAO items;
    private final DigitalResourceDAO resources;
    private final ReportDAO reports;

    public ReportService(DatabaseManager db, LoanDAO loans, PhysicalItemDAO items,
                         DigitalResourceDAO resources, ReportDAO reports) {
        this.db = db;
        this.loans = loans;
        this.items = items;
        this.resources = resources;
        this.reports = reports;
    }

    /** Student dashboard numbers. Items due within two days are on time but close to their due date. */
    public DashboardCounts dashboard(User user, LocalDate today) {
        return Sql.read(db, c -> new DashboardCounts(
                loans.countOpenByUser(c, user.id()),
                loans.countDueBetween(c, user.id(), today, today.plusDays(2)),
                resources.countDistinctDownloadsByUser(c, user.id()),
                items.countDonatedBy(c, user.id())));
    }

    /** Admin and faculty analytics for a date range, inclusive. */
    public ImpactSummary impact(LocalDate from, LocalDate to) {
        return Sql.read(db, c -> new ImpactSummary(
                loans.countAll(c, from, to),
                reports.countDigitalDownloads(c, from, to),
                reports.countActiveUsers(c, from, to),
                loans.countOverdue(c),
                reports.topSubjects(c, from, to, TOP_SUBJECTS)));
    }

    public Report borrowingLogReport() {
        List<LoanDetail> rows = Sql.read(db, loans::listAll);
        List<List<Object>> lines = new ArrayList<>();
        for (LoanDetail d : rows) {
            var loan = d.loan();
            lines.add(List.of(
                    loan.id(),
                    d.borrowerName(),
                    d.borrowerSchoolId(),
                    d.itemTitle(),
                    loan.borrowDate().toString(),
                    loan.dueDate().toString(),
                    loan.returnDate() == null ? "" : loan.returnDate().toString(),
                    loan.loanPeriodDays(),
                    loan.extensionCount(),
                    loan.status().dbValue(),
                    loan.feeAmount(),
                    loan.feeWaived() ? "Yes" : "No"));
        }
        return new Report("Borrowing Log",
                List.of("Loan ID", "Borrower", "School ID", "Item", "Borrowed", "Due", "Returned",
                        "Period (days)", "Extensions", "Status", "Fee (PHP)", "Fee waived"),
                lines);
    }

    public Report inventoryReport() {
        List<PhysicalItem> rows = Sql.read(db, items::getAllItems);
        List<List<Object>> lines = new ArrayList<>();
        for (PhysicalItem item : rows) {
            lines.add(List.of(
                    item.id(),
                    item.title(),
                    item.brandModel() == null ? "" : item.brandModel(),
                    item.category().dbValue(),
                    item.condition().dbValue(),
                    String.join(", ", item.levels().stream().map(l -> l.dbValue()).toList()),
                    item.pickupPoint(),
                    item.donorName() == null ? "Library stock" : item.donorName(),
                    item.givingType().dbValue(),
                    item.status().dbValue()));
        }
        return new Report("Inventory",
                List.of("Item ID", "Title", "Brand / Model", "Category", "Condition", "Recommended for",
                        "Pickup point", "Donor", "Giving", "Status"),
                lines);
    }

    public record DashboardCounts(int activeLoans, int dueWithinTwoDays, int downloadedModules, int itemsDonated) {
    }

    public record ImpactSummary(int physicalLoans, int digitalDownloads, int activeUsers, int overdueItems,
                                Map<String, Integer> topSubjects) {
    }
}
