package com.eduaccess.service;

import com.eduaccess.dao.MarketplaceDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.MarketplaceCategory;
import com.eduaccess.model.PeerBorrow;
import com.eduaccess.model.PeerBorrowStatus;
import com.eduaccess.model.PeerMessage;
import com.eduaccess.model.Role;
import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/** Service handling peer-to-peer student marketplace listings, borrows, and chat. */
public final class MarketplaceService {

    private final DatabaseManager db;
    private final MarketplaceDAO dao;
    private final UserDAO users;

    public MarketplaceService(DatabaseManager db, MarketplaceDAO dao, UserDAO users) {
        this.db = db;
        this.dao = dao;
        this.users = users;
    }

    public record ListingForm(
            String title,
            String description,
            MarketplaceCategory category,
            ItemCondition condition,
            EducationLevel educationLevel,
            BigDecimal dailyFee,
            String pickupPoint,
            Path photoFile
    ) {}

    public static boolean mayTrade(User user) {
        if (user == null || user.role() != Role.STUDENT) {
            return false;
        }
        EducationLevel level = user.educationLevel();
        return level == EducationLevel.SHS || level == EducationLevel.COLLEGE;
    }

    public boolean canTrade(User user) {
        if (!mayTrade(user)) {
            return false;
        }
        return Sql.read(db, c -> !users.isMinor(c, user.id()));
    }

    public boolean isMinor(User user) {
        return Sql.read(db, c -> users.isMinor(c, user.id()));
    }

    public long postListing(User owner, ListingForm form) {
        requireStudentTrader(owner);
        validateForm(form);
        String photoPath = form.photoFile() != null ? InventoryService.copyPhoto(form.photoFile()) : null;
        return Sql.write(db, c -> dao.insertListing(c, new MarketplaceDAO.NewListing(
                owner.id(),
                form.title().strip(),
                form.description().strip(),
                form.category(),
                form.condition(),
                form.educationLevel(),
                form.dailyFee() != null ? form.dailyFee() : BigDecimal.ZERO,
                form.pickupPoint().strip(),
                photoPath
        )));
    }

    public void updateListing(User owner, long listingId, ListingForm form) {
        requireStudentTrader(owner);
        validateForm(form);
        String photoPath = form.photoFile() != null ? InventoryService.copyPhoto(form.photoFile()) : null;
        Sql.write(db, c -> {
            StudentListing listing = dao.findListing(c, listingId)
                    .orElseThrow(() -> new ServiceException("Listing not found."));
            if (listing.ownerId() != owner.id()) {
                throw new ServiceException("You can only edit your own listings.");
            }
            dao.updateListing(c, listingId, form.title().strip(), form.description().strip(),
                    form.category(), form.condition(), form.educationLevel(),
                    form.dailyFee() != null ? form.dailyFee() : BigDecimal.ZERO,
                    form.pickupPoint().strip(), photoPath);
            return null;
        });
    }

    public void pauseListing(User owner, long listingId) {
        setListingStatus(owner, listingId, "Paused");
    }

    public void resumeListing(User owner, long listingId) {
        setListingStatus(owner, listingId, "Active");
    }

    public void removeListing(User owner, long listingId) {
        setListingStatus(owner, listingId, "Removed");
    }

    private void setListingStatus(User owner, long listingId, String status) {
        requireStudentTrader(owner);
        Sql.write(db, c -> {
            StudentListing listing = dao.findListing(c, listingId)
                    .orElseThrow(() -> new ServiceException("Listing not found."));
            if (listing.ownerId() != owner.id()) {
                throw new ServiceException("You can only modify your own listings.");
            }
            dao.setListingStatus(c, listingId, status);
            return null;
        });
    }

    public List<StudentListing> browse(String keyword, MarketplaceCategory category, EducationLevel level) {
        return Sql.read(db, c -> dao.browseListings(c, category, level, keyword));
    }

    public List<StudentListing> myListings(User owner) {
        return Sql.read(db, c -> dao.listingsByOwner(c, owner.id()));
    }

    public StudentListing getListing(long listingId) {
        return Sql.read(db, c -> dao.findListing(c, listingId)
                .orElseThrow(() -> new ServiceException("Listing not found.")));
    }

    public long requestBorrow(User borrower, long listingId, String note) {
        requireStudentTrader(borrower);
        return Sql.write(db, c -> {
            StudentListing listing = dao.findListing(c, listingId)
                    .orElseThrow(() -> new ServiceException("Listing not found."));
            if (!"Active".equals(listing.status())) {
                throw new ServiceException("This listing is not currently active.");
            }
            if (listing.ownerId() == borrower.id()) {
                throw new ServiceException("You cannot borrow your own listing.");
            }
            return dao.insertBorrow(c, listingId, borrower.id(), listing.ownerId(), listing.dailyFee(), note);
        });
    }

    public void acceptBorrow(User owner, long borrowId) {
        updateBorrowStatus(owner, borrowId, PeerBorrowStatus.REQUESTED, PeerBorrowStatus.ACCEPTED, true);
    }

    public void declineBorrow(User owner, long borrowId) {
        updateBorrowStatus(owner, borrowId, PeerBorrowStatus.REQUESTED, PeerBorrowStatus.DECLINED, true);
    }

    public void cancelBorrow(User borrower, long borrowId) {
        Sql.write(db, c -> {
            PeerBorrow b = dao.findBorrow(c, borrowId)
                    .orElseThrow(() -> new ServiceException("Borrow request not found."));
            if (b.borrowerId() != borrower.id()) {
                throw new ServiceException("You can only cancel your own borrow requests.");
            }
            boolean success = dao.moveBorrow(c, borrowId, PeerBorrowStatus.REQUESTED, PeerBorrowStatus.CANCELLED);
            if (!success) {
                throw new ServiceException("This request can no longer be cancelled.");
            }
            return null;
        });
    }

    public void markBorrowed(User owner, long borrowId) {
        updateBorrowStatus(owner, borrowId, PeerBorrowStatus.ACCEPTED, PeerBorrowStatus.BORROWED, true);
    }

    public void markReturned(User owner, long borrowId) {
        updateBorrowStatus(owner, borrowId, PeerBorrowStatus.BORROWED, PeerBorrowStatus.RETURNED, true);
    }

    private void updateBorrowStatus(User user, long borrowId, PeerBorrowStatus from, PeerBorrowStatus to, boolean ownerOnly) {
        requireStudentTrader(user);
        Sql.write(db, c -> {
            PeerBorrow b = dao.findBorrow(c, borrowId)
                    .orElseThrow(() -> new ServiceException("Borrow record not found."));
            if (ownerOnly && b.ownerId() != user.id()) {
                throw new ServiceException("Only the listing owner can perform this action.");
            }
            boolean success = dao.moveBorrow(c, borrowId, from, to);
            if (!success) {
                throw new ServiceException("Action could not be completed from current state.");
            }
            return null;
        });
    }

    public List<PeerBorrow> myBorrowings(User borrower) {
        return Sql.read(db, c -> dao.borrowsByBorrower(c, borrower.id()));
    }

    public List<PeerBorrow> incomingRequests(User owner) {
        return Sql.read(db, c -> dao.borrowsByOwner(c, owner.id()));
    }

    public List<PeerBorrow> borrowsForListing(long listingId) {
        return Sql.read(db, c -> dao.borrowsByListing(c, listingId));
    }

    public long sendMessage(User sender, long listingId, String body) {
        if (sender == null || sender.role() != Role.STUDENT) {
            throw new ServiceException("Only students can send messages.");
        }
        if (body == null || body.isBlank()) {
            throw new ServiceException("Message cannot be blank.");
        }
        if (body.length() > 1000) {
            throw new ServiceException("Message is too long (maximum 1000 characters).");
        }
        return Sql.write(db, c -> {
            StudentListing listing = dao.findListing(c, listingId)
                    .orElseThrow(() -> new ServiceException("Listing not found."));
            return dao.insertMessage(c, listingId, sender.id(), body.strip());
        });
    }

    public List<PeerMessage> thread(long listingId, User viewer) {
        return Sql.read(db, c -> dao.messagesByListing(c, listingId));
    }

    public List<StudentListing> conversations(User user) {
        return Sql.read(db, c -> dao.conversationsForUser(c, user.id()));
    }

    public int unreadRequestsAndMessagesCount(User user) {
        if (!mayTrade(user)) {
            return 0;
        }
        return Sql.read(db, c -> {
            List<PeerBorrow> incoming = dao.borrowsByOwner(c, user.id());
            int pending = (int) incoming.stream().filter(b -> b.status() == PeerBorrowStatus.REQUESTED).count();
            return pending;
        });
    }

    public User getUserProfile(long userId) {
        return Sql.read(db, c -> users.getProfile(c, userId).orElse(null));
    }

    private void requireStudentTrader(User user) {
        if (user == null) {
            throw new ServiceException("Please sign in first.");
        }
        if (user.role() != Role.STUDENT) {
            throw new ServiceException("The Student Marketplace is for students. Staff manage library lending.");
        }
        if (!mayTrade(user)) {
            throw new ServiceException("The Student Marketplace is restricted to Senior High School and College students.");
        }
        if (Sql.read(db, c -> users.isMinor(c, user.id()))) {
            throw new ServiceException("Student Marketplace trading is restricted to students 18 years or older.");
        }
    }

    private void validateForm(ListingForm form) {
        if (form.title() == null || form.title().isBlank()) {
            throw new ServiceException("Enter an item title.");
        }
        if (form.description() == null || form.description().isBlank()) {
            throw new ServiceException("Enter a description.");
        }
        if (form.category() == null) {
            throw new ServiceException("Choose a category.");
        }
        if (form.condition() == null) {
            throw new ServiceException("Choose item condition.");
        }
        if (form.educationLevel() == null) {
            throw new ServiceException("Choose target education level.");
        }
        if (form.dailyFee() != null && form.dailyFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("Daily fee cannot be negative.");
        }
        if (form.pickupPoint() == null || form.pickupPoint().isBlank()) {
            throw new ServiceException("Enter a pickup point.");
        }
    }
}
