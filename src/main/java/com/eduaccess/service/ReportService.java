package com.eduaccess.service;

import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.dao.ExportDAO;
import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.ReportDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
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
    private final ExportDAO export = new ExportDAO();

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

    // ----- Excel and CSV exports ------------------------------------------------------------------------------

    /**
     * One report, optionally limited to one education level (null means every level). For loans, downloads,
     * offers and users the level is the person's. For inventory and the digital library it is the item's.
     */
    public Report report(ReportType type, EducationLevel level) {
        String code = level == null || !type.levelFilter() ? null : level.dbValue();
        String name = type.label() + (code == null ? "" : " - " + code);
        Object[] params = type.levelFilter() ? new Object[] {code} : new Object[0];
        ExportDAO.Table table = Sql.read(db, c -> export.run(c, sql(type), params));
        return new Report(name, table.headers(), table.rows());
    }

    /** Every report, one per sheet, for the "Export full database" workbook. */
    public List<Report> fullDatabase() {
        List<Report> all = new ArrayList<>();
        for (ReportType type : ReportType.values()) {
            all.add(report(type, null));
        }
        return all;
    }

    /** A student's own loans, open and returned, for "Download history" on My Loans. */
    public Report loanHistory(User user) {
        ExportDAO.Table table = Sql.read(db, c -> export.run(c, """
                SELECT i.title AS "Item", i.category AS "Category", l.borrow_date AS "Borrowed",
                       l.due_date AS "Due", l.return_date AS "Returned", l.loan_period_days AS "Period (days)",
                       l.extension_count AS "Extensions", l.status AS "Status",
                       CASE WHEN l.fee_waived = 1 THEN 0 ELSE l.fee_amount END AS "Fee (PHP)",
                       CASE l.fee_waived WHEN 1 THEN 'Yes' ELSE 'No' END AS "Fee waived"
                FROM loans l JOIN physical_items i ON i.item_id = l.item_id
                WHERE l.borrower_id = ?
                ORDER BY l.borrow_date DESC, l.loan_id DESC
                """, user.id()));
        return new Report("My loan history", table.headers(), table.rows());
    }

    /**
     * The query behind each report. Reports with a level filter take one parameter, the education level text or
     * null, written as ?1 so the query can use it twice.
     */
    private static String sql(ReportType type) {
        return switch (type) {
            case BORROWING_LOG -> """
                    SELECT l.loan_id AS "Loan ID", u.full_name AS "Borrower", u.school_id AS "School ID",
                           u.education_level AS "Borrower level", i.title AS "Item", i.category AS "Category",
                           l.borrow_date AS "Borrowed", l.due_date AS "Due", l.return_date AS "Returned",
                           l.loan_period_days AS "Period (days)", l.extension_count AS "Extensions",
                           l.status AS "Status", l.fee_amount AS "Fee (PHP)",
                           CASE l.fee_waived WHEN 1 THEN 'Yes' ELSE 'No' END AS "Fee waived"
                    FROM loans l
                    JOIN users u ON u.user_id = l.borrower_id
                    JOIN physical_items i ON i.item_id = l.item_id
                    WHERE (?1 IS NULL OR u.education_level = ?1)
                    ORDER BY l.borrow_date DESC, l.loan_id DESC
                    """;
            case OVERDUE_FEES -> """
                    SELECT l.loan_id AS "Loan ID", u.full_name AS "Borrower", u.school_id AS "School ID",
                           u.education_level AS "Borrower level", i.title AS "Item", l.due_date AS "Due",
                           CAST(julianday(date('now', 'localtime')) - julianday(l.due_date) AS INTEGER)
                               AS "Days late",
                           CASE WHEN l.fee_waived = 1 THEN 0 ELSE l.fee_amount END AS "Fee due (PHP)",
                           CASE l.fee_waived WHEN 1 THEN 'Yes' ELSE 'No' END AS "Fee waived"
                    FROM loans l
                    JOIN users u ON u.user_id = l.borrower_id
                    JOIN physical_items i ON i.item_id = l.item_id
                    WHERE l.status = 'Overdue' AND (?1 IS NULL OR u.education_level = ?1)
                    ORDER BY l.due_date
                    """;
            case INVENTORY -> """
                    SELECT i.item_id AS "Item ID", i.title AS "Title", i.brand_model AS "Brand / model",
                           i.category AS "Category",
                           CASE i.category WHEN 'Textbooks' THEN 'Physical Library' ELSE 'Physical Hub' END
                               AS "Listed in",
                           i.condition AS "Condition",
                           (SELECT group_concat(education_level, ', ') FROM physical_item_levels l
                               WHERE l.item_id = i.item_id) AS "Recommended for",
                           i.pickup_point AS "Pickup point", COALESCE(d.full_name, 'Library stock') AS "Donor",
                           i.type_of_giving AS "Giving", i.status AS "Status", i.created_at AS "Added"
                    FROM physical_items i
                    LEFT JOIN users d ON d.user_id = i.donor_id
                    WHERE (?1 IS NULL OR EXISTS (SELECT 1 FROM physical_item_levels l
                               WHERE l.item_id = i.item_id AND l.education_level = ?1))
                    ORDER BY i.item_id
                    """;
            case DONATIONS -> """
                    SELECT i.item_id AS "Item ID", i.created_at AS "Submitted", d.full_name AS "Donor",
                           d.school_id AS "School ID", d.education_level AS "Donor level", i.title AS "Title",
                           i.brand_model AS "Brand / model", i.category AS "Category", i.condition AS "Condition",
                           i.type_of_giving AS "Giving", i.pickup_point AS "Drop-off",
                           CASE i.status WHEN 'Pending' THEN 'Waiting for review' WHEN 'Rejected' THEN 'Rejected'
                               ELSE 'Accepted' END AS "Review", i.status AS "Item status"
                    FROM physical_items i
                    JOIN users d ON d.user_id = i.donor_id
                    WHERE (?1 IS NULL OR d.education_level = ?1)
                    ORDER BY i.created_at DESC, i.item_id DESC
                    """;
            case DIGITAL_DOWNLOADS -> """
                    SELECT dl.downloaded_at AS "Downloaded", u.full_name AS "Student", u.school_id AS "School ID",
                           u.education_level AS "Student level", r.title AS "Resource", r.subject AS "Subject",
                           r.education_level AS "Resource level", r.category_tab AS "Collection"
                    FROM digital_downloads dl
                    JOIN users u ON u.user_id = dl.user_id
                    JOIN digital_resources r ON r.resource_id = dl.resource_id
                    WHERE (?1 IS NULL OR u.education_level = ?1)
                    ORDER BY dl.downloaded_at DESC
                    """;
            case DIGITAL_LIBRARY -> """
                    SELECT r.resource_id AS "Resource ID", r.title AS "Title", r.category_tab AS "Collection",
                           r.subject AS "Subject", r.education_level AS "Level", r.file_size_mb AS "Size (MB)",
                           CASE r.is_cached_offline WHEN 1 THEN 'Yes' ELSE 'No' END AS "Cached offline",
                           (SELECT COUNT(*) FROM digital_downloads dl WHERE dl.resource_id = r.resource_id)
                               AS "Downloads",
                           r.source AS "Source", r.license AS "License", r.file_path AS "File"
                    FROM digital_resources r
                    WHERE (?1 IS NULL OR r.education_level = ?1)
                    ORDER BY r.category_tab, r.title
                    """;
            case RENTAL_REQUESTS -> """
                    SELECT r.request_id AS "Request ID", r.title AS "Item needed", r.category AS "Category",
                           r.purpose AS "Purpose", r.quantity_needed AS "Quantity",
                           (SELECT COUNT(*) FROM rental_offers o WHERE o.request_id = r.request_id
                               AND o.status IN ('Approved', 'Checked-In', 'Returned')) AS "Approved",
                           (SELECT COUNT(*) FROM rental_offers o WHERE o.request_id = r.request_id) AS "Offers",
                           r.min_condition AS "Minimum condition", r.offers_close_on AS "Offers close",
                           r.needed_from AS "Needed from", r.needed_until AS "Needed until",
                           r.rental_fee AS "Fee per item (PHP)", r.dropoff_point AS "Drop-off",
                           r.status AS "Status", u.full_name AS "Posted by", r.created_at AS "Posted"
                    FROM rental_requests r
                    JOIN users u ON u.user_id = r.created_by
                    ORDER BY r.request_id DESC
                    """;
            case RENTAL_OFFERS -> """
                    SELECT o.offer_id AS "Offer ID", r.title AS "Request", u.full_name AS "Owner",
                           u.school_id AS "School ID", u.education_level AS "Owner level",
                           o.item_name AS "Item", o.brand_model AS "Brand / model", o.condition AS "Condition offered",
                           o.status AS "Status", o.agreed_fee AS "Fee (PHP)",
                           CASE o.fee_paid WHEN 1 THEN 'Yes' ELSE 'No' END AS "Fee paid",
                           o.condition_in AS "Condition in", o.condition_out AS "Condition out",
                           o.damage_note AS "Damage note", o.decline_reason AS "Reason",
                           o.offered_at AS "Offered", o.checked_in_at AS "Checked in", o.returned_at AS "Returned"
                    FROM rental_offers o
                    JOIN rental_requests r ON r.request_id = o.request_id
                    JOIN users u ON u.user_id = o.owner_id
                    WHERE (?1 IS NULL OR u.education_level = ?1)
                    ORDER BY o.offer_id DESC
                    """;
            case USERS -> """
                    SELECT u.user_id AS "User ID", u.school_id AS "School ID", u.full_name AS "Full name",
                           CASE u.role WHEN 'Admin' THEN 'Library Admin' ELSE 'Student' END AS "Role",
                           u.education_level AS "Level", u.program AS "Program", u.year_level AS "Year",
                           u.campus AS "Campus", u.intention AS "Uses EduAccess to",
                           CASE WHEN g.user_id IS NULL THEN 'No' ELSE 'Yes' END AS "Under 18 (guardian consent)",
                           (SELECT COUNT(*) FROM loans l WHERE l.borrower_id = u.user_id) AS "Loans",
                           (SELECT COUNT(*) FROM physical_items i WHERE i.donor_id = u.user_id) AS "Donations",
                           u.created_at AS "Registered"
                    FROM users u
                    LEFT JOIN guardian_consents g ON g.user_id = u.user_id
                    WHERE (?1 IS NULL OR u.education_level = ?1)
                    ORDER BY u.role, u.full_name
                    """;
            case ANNOUNCEMENTS -> """
                    SELECT a.announcement_id AS "ID", a.title AS "Title", a.body AS "Text",
                           u.full_name AS "Posted by", a.posted_on AS "Posted", a.expires_on AS "Ends",
                           CASE WHEN a.removed = 1 THEN 'Removed'
                                WHEN a.expires_on IS NOT NULL AND a.expires_on < date('now', 'localtime')
                                    THEN 'Ended'
                                ELSE 'Showing' END AS "Status"
                    FROM announcements a
                    JOIN users u ON u.user_id = a.posted_by
                    ORDER BY a.announcement_id DESC
                    """;
        };
    }

    /** Physical loans and digital downloads per education level, for the impact chart. Arrays are {loans, downloads}. */
    public Map<String, int[]> usageByLevel(LocalDate from, LocalDate to) {
        return Sql.read(db, c -> reports.usageByLevel(c, from, to));
    }

    public List<ReportDAO.Activity> recentActivity(int limit) {
        return Sql.read(db, c -> reports.recentActivity(c, limit));
    }

    public record DashboardCounts(int activeLoans, int dueWithinTwoDays, int downloadedModules, int itemsDonated) {
    }

    public record ImpactSummary(int physicalLoans, int digitalDownloads, int activeUsers, int overdueItems,
                                Map<String, Integer> topSubjects) {
    }
}
