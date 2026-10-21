package com.eduaccess.ui;

import com.eduaccess.dao.AnnouncementDAO;
import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.MarketplaceDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.RentalDAO;
import com.eduaccess.dao.ReportDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.service.AnnouncementService;
import com.eduaccess.service.AuthService;
import com.eduaccess.service.DigitalLibraryService;
import com.eduaccess.service.FileExportService;
import com.eduaccess.service.InventoryService;
import com.eduaccess.service.LoanPolicy;
import com.eduaccess.service.LoanRules;
import com.eduaccess.service.LoanService;
import com.eduaccess.service.MarketplaceService;
import com.eduaccess.service.RentalService;
import com.eduaccess.service.ReportService;
import com.eduaccess.service.Session;
import com.eduaccess.service.UsbService;
import java.nio.file.Path;

/** Wires the DAOs and services together once, and holds the signed-in session for all screens. */
public final class AppContext {

    public final Session session = new Session();
    final AuthService auth;
    final InventoryService inventory;
    final LoanService loans;
    final DigitalLibraryService library;
    final ReportService reports;
    final RentalService rentals;
    final AnnouncementService announcements;
    final MarketplaceService marketplace;
    final FileExportService exporter = new FileExportService();
    final UsbService usb = new UsbService();

    public AppContext(DatabaseManager db) {
        UserDAO users = new UserDAO();
        PhysicalItemDAO items = new PhysicalItemDAO();
        LoanDAO loanDao = new LoanDAO();
        DigitalResourceDAO resources = new DigitalResourceDAO();
        this.auth = new AuthService(db, users);
        this.inventory = new InventoryService(db, items);
        this.loans = new LoanService(db, loanDao, items, new LoanRules(LoanPolicy.DEFAULT));
        this.library = new DigitalLibraryService(db, resources, Path.of("storage", "digital"));
        this.reports = new ReportService(db, loanDao, items, resources, new ReportDAO());
        this.rentals = new RentalService(db, new RentalDAO());
        this.announcements = new AnnouncementService(db, new AnnouncementDAO());
        this.marketplace = new MarketplaceService(db, new MarketplaceDAO(), users);
    }
}
