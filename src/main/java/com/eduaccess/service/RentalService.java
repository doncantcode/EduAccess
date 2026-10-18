package com.eduaccess.service;

import com.eduaccess.dao.RentalDAO;
import com.eduaccess.dao.RentalDAO.NewOffer;
import com.eduaccess.dao.RentalDAO.NewRequest;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.RentalRequestStatus;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Library Rental Requests. Staff post what the library needs to rent for a short time and what it pays, students
 * offer their own items, and staff approve, check in and hand back those items. The lifecycles are documented in
 * schema.sql.
 *
 * <p>Only students 18 or older may offer items (a rental is a contract, and RA 10173 asks for care with minors).
 * EduAccess stores no birth date, so the rule has three layers: Elementary and Junior High accounts cannot offer at
 * all, the student confirms their age on the offer, and staff check the school ID at check-in.
 */
public final class RentalService {

    static final String REASON_FILLED = "The library already has enough items for this request.";
    static final String REASON_CLOSED = "Offers closed before the library could use this item.";
    static final String REASON_CANCELLED = "The library cancelled this request.";
    static final String REASON_NO_SHOW = "The item was not handed over in time.";
    public static final String AGE_RULE = "Only students 18 or older can offer items for rent.";

    private final DatabaseManager db;
    private final RentalDAO rentals;
    private final UserDAO users = new UserDAO();

    public RentalService(DatabaseManager db, RentalDAO rentals) {
        this.db = db;
        this.rentals = rentals;
    }

    // ----- Staff: requests -------------------------------------------------------------------------------------

    /** Staff post a request. It is open for offers straight away. */
    public long createRequest(User staff, RequestForm form, LocalDate today) {
        Access.requireAdmin(staff, "create rental requests");
        validate(form, today);
        NewRequest request = new NewRequest(
                staff.id(),
                form.title().strip(),
                form.description().strip(),
                form.purpose().strip(),
                form.category(),
                form.quantityNeeded(),
                form.minCondition(),
                form.offersCloseOn(),
                form.neededFrom(),
                form.neededUntil(),
                fee(form.rentalFee()),
                form.dropoffPoint().strip());
        return Sql.write(db, c -> rentals.insertRequest(c, request));
    }

    /** Cancels an Open or Filled request. Waiting offers are declined and approved ones cancelled. */
    public void cancelRequest(User staff, long requestId) {
        Access.requireAdmin(staff, "cancel rental requests");
        Sql.write(db, c -> {
            RentalRequest request = requireRequest(c, requestId);
            if (request.status() != RentalRequestStatus.OPEN && request.status() != RentalRequestStatus.FILLED) {
                throw new ServiceException("Only open or filled requests can be cancelled.");
            }
            rentals.moveRequest(c, requestId, request.status(), RentalRequestStatus.CANCELLED);
            rentals.declineWaitingOffers(c, requestId, REASON_CANCELLED);
            rentals.cancelApprovedOffers(c, requestId, REASON_CANCELLED);
            return null;
        });
    }

    // ----- Staff: offers ---------------------------------------------------------------------------------------

    /**
     * Approves an offer. When the approved count reaches the quantity needed, the request becomes Filled and the
     * offers still waiting are declined. Staff may still approve after offers close, until the request settles.
     */
    public void approve(User staff, long offerId) {
        Access.requireAdmin(staff, "approve offers");
        Sql.write(db, c -> {
            RentalOffer offer = requireOffer(c, offerId);
            RentalRequest request = requireRequest(c, offer.requestId());
            if (request.status() != RentalRequestStatus.OPEN) {
                throw new ServiceException("This request is " + request.status().dbValue().toLowerCase()
                        + ", so it cannot take more items.");
            }
            if (!rentals.decide(c, offerId, OfferStatus.APPROVED, staff.id(), request.rentalFee(), null)) {
                throw new ServiceException("Only offers that are waiting for review can be approved.");
            }
            RentalRequest updated = requireRequest(c, request.id());
            if (updated.approvedCount() >= updated.quantityNeeded()) {
                rentals.moveRequest(c, request.id(), RentalRequestStatus.OPEN, RentalRequestStatus.FILLED);
                rentals.declineWaitingOffers(c, request.id(), REASON_FILLED);
            }
            return null;
        });
    }

    public void decline(User staff, long offerId, String reason) {
        Access.requireAdmin(staff, "decline offers");
        String text = reason == null || reason.isBlank() ? "This item does not fit the request." : reason.strip();
        Sql.write(db, c -> {
            if (!rentals.decide(c, offerId, OfferStatus.DECLINED, staff.id(), null, text)) {
                throw new ServiceException("Only offers that are waiting for review can be declined.");
            }
            return null;
        });
    }

    /**
     * The owner handed the item over at the counter. The condition is recorded as it arrived, and staff confirm
     * they checked the owner's school ID shows 18 or older.
     */
    public void checkIn(User staff, long offerId, ItemCondition condition, boolean ageChecked) {
        Access.requireAdmin(staff, "check in rented items");
        if (!ageChecked) {
            throw new ServiceException("Check the owner's school ID first. " + AGE_RULE);
        }
        if (condition == null) {
            throw new ServiceException("Record the condition the item arrived in.");
        }
        Sql.write(db, c -> {
            if (!rentals.checkIn(c, offerId, condition)) {
                throw new ServiceException("Only approved offers can be checked in.");
            }
            return null;
        });
    }

    /** An approved item never arrived. The offer is cancelled and the request settles. */
    public void markNoShow(User staff, long offerId, LocalDate today) {
        Access.requireAdmin(staff, "update offers");
        Sql.write(db, c -> {
            RentalOffer offer = requireOffer(c, offerId);
            if (offer.status() != OfferStatus.APPROVED) {
                throw new ServiceException("Only approved offers that were never handed over can be marked.");
            }
            setReason(c, offerId, OfferStatus.CANCELLED, REASON_NO_SHOW);
            settle(c, offer.requestId(), today);
            return null;
        });
    }

    /** The library gives the item back to its owner. The last return completes the request. */
    public void returnToOwner(User staff, long offerId, ItemCondition condition, String damageNote, boolean feePaid,
                              LocalDate today) {
        Access.requireAdmin(staff, "return rented items");
        if (condition == null) {
            throw new ServiceException("Record the condition the item is returned in.");
        }
        String note = damageNote == null || damageNote.isBlank() ? null : damageNote.strip();
        Sql.write(db, c -> {
            RentalOffer offer = requireOffer(c, offerId);
            if (!rentals.returnToOwner(c, offerId, condition, note, feePaid)) {
                throw new ServiceException("Only items the library has checked in can be returned.");
            }
            settle(c, offer.requestId(), today);
            return null;
        });
    }

    public List<RentalOffer> offersFor(User staff, long requestId) {
        Access.requireAdmin(staff, "see every offer");
        return Sql.read(db, c -> rentals.offersForRequest(c, requestId));
    }

    /** Offers waiting for a staff decision, across all requests. */
    public int offersAwaitingReview() {
        return Sql.read(db, c -> rentals.countOffersWithStatus(c, OfferStatus.OFFERED));
    }

    // ----- Students --------------------------------------------------------------------------------------------

    /**
     * False for staff, and for Elementary and Junior High students, who are all under 18. Anyone else may offer,
     * after confirming their age.
     */
    public static boolean mayOffer(User user) {
        return user.role() == Role.STUDENT
                && user.educationLevel() != EducationLevel.ELEMENTARY
                && user.educationLevel() != EducationLevel.JHS;
    }

    /** Whether this user may offer items: a student who is not Elementary, Junior High or registered as a minor. */
    public boolean canOffer(User user) {
        return mayOffer(user) && !Sql.read(db, c -> users.isMinor(c, user.id()));
    }

    /** A student offers one of their items for the library to rent. */
    public long offer(User owner, long requestId, OfferForm form, LocalDate today) {
        if (owner.role() != Role.STUDENT) {
            throw new ServiceException("Only students can offer items. Staff manage the requests.");
        }
        if (!mayOffer(owner)) {
            throw new ServiceException(AGE_RULE + " Elementary and Junior High students cannot offer items.");
        }
        if (!form.adultConfirmed()) {
            throw new ServiceException(AGE_RULE + " Please confirm you are 18 or older.");
        }
        refreshDeadlines(today);
        if (form.itemName() == null || form.itemName().isBlank()) {
            throw new ServiceException("Enter the name of the item you are offering.");
        }
        if (form.condition() == null) {
            throw new ServiceException("Choose the condition of your item.");
        }
        if (!form.agreed()) {
            throw new ServiceException("Please confirm you can bring the item to the library and collect it after.");
        }
        return Sql.write(db, c -> {
            if (users.isMinor(c, owner.id())) {
                throw new ServiceException(AGE_RULE + " Your account was registered with a guardian's consent.");
            }
            RentalRequest request = requireRequest(c, requestId);
            if (!request.acceptsOffers(today)) {
                throw new ServiceException("This request is no longer taking offers.");
            }
            if (!form.condition().meets(request.minCondition())) {
                throw new ServiceException("This request needs items in " + request.minCondition().dbValue()
                        + " condition or better.");
            }
            boolean alreadyOffered = rentals.offersByOwner(c, owner.id()).stream()
                    .anyMatch(o -> o.requestId() == requestId && o.isLive());
            if (alreadyOffered) {
                throw new ServiceException("You already have an offer on this request.");
            }
            String photoPath = InventoryService.copyPhoto(form.photo());
            return rentals.insertOffer(c, new NewOffer(
                    requestId,
                    owner.id(),
                    form.itemName().strip(),
                    blankToNull(form.brandModel()),
                    blankToNull(form.description()),
                    form.condition(),
                    photoPath,
                    blankToNull(form.ownerNote())));
        });
    }

    /** The owner takes an offer back, any time before the library has the item. */
    public void withdraw(User owner, long offerId, LocalDate today) {
        Sql.write(db, c -> {
            RentalOffer offer = requireOffer(c, offerId);
            if (offer.ownerId() != owner.id()) {
                throw new ServiceException("You can only withdraw your own offers.");
            }
            if (offer.status() != OfferStatus.OFFERED && offer.status() != OfferStatus.APPROVED) {
                throw new ServiceException("You can only withdraw an offer before the library has your item.");
            }
            rentals.withdraw(c, offerId, offer.status());
            settle(c, offer.requestId(), today);
            return null;
        });
    }

    public List<RentalOffer> myOffers(User owner) {
        return Sql.read(db, c -> rentals.offersByOwner(c, owner.id()));
    }

    // ----- Everyone --------------------------------------------------------------------------------------------

    /** Requests that are taking offers, soonest deadline first. */
    public List<RentalRequest> openRequests(LocalDate today) {
        refreshDeadlines(today);
        return Sql.read(db, c -> rentals.requestsWithStatus(c, RentalRequestStatus.OPEN)).stream()
                .filter(request -> request.acceptsOffers(today))
                .toList();
    }

    /** Every request, newest first. */
    public List<RentalRequest> allRequests(LocalDate today) {
        refreshDeadlines(today);
        return Sql.read(db, rentals::allRequests);
    }

    public RentalRequest request(long requestId) {
        return Sql.read(db, c -> requireRequest(c, requestId));
    }

    /**
     * Brings open requests in line with today's date, the way overdue loans are refreshed. After offers close, a
     * request with at least one approved item becomes Filled and the offers still waiting are declined. A request
     * with nothing approved stays open for staff review until its start date, then expires.
     * Returns how many requests changed.
     */
    public int refreshDeadlines(LocalDate today) {
        return Sql.write(db, c -> {
            int changed = 0;
            for (RentalRequest r : rentals.requestsWithStatus(c, RentalRequestStatus.OPEN)) {
                if (!today.isAfter(r.offersCloseOn())) {
                    continue;
                }
                if (r.approvedCount() > 0) {
                    rentals.moveRequest(c, r.id(), RentalRequestStatus.OPEN, RentalRequestStatus.FILLED);
                    rentals.declineWaitingOffers(c, r.id(), REASON_CLOSED);
                    changed++;
                } else if (r.pendingCount() == 0 || today.isAfter(r.neededFrom())) {
                    rentals.moveRequest(c, r.id(), RentalRequestStatus.OPEN, RentalRequestStatus.EXPIRED);
                    rentals.declineWaitingOffers(c, r.id(), REASON_CLOSED);
                    changed++;
                }
            }
            return changed;
        });
    }

    // ----- Rules -----------------------------------------------------------------------------------------------

    /**
     * After an offer drops out or comes back, a Filled request reopens if it is short and still inside its offer
     * window, expires if nothing is left, or completes when every item has been returned.
     */
    private void settle(Connection c, long requestId, LocalDate today) throws SQLException {
        RentalRequest r = requireRequest(c, requestId);
        if (r.status() != RentalRequestStatus.FILLED) {
            return;
        }
        if (r.approvedCount() < r.quantityNeeded() && !today.isAfter(r.offersCloseOn())) {
            rentals.moveRequest(c, requestId, RentalRequestStatus.FILLED, RentalRequestStatus.OPEN);
        } else if (r.approvedCount() == 0) {
            rentals.moveRequest(c, requestId, RentalRequestStatus.FILLED, RentalRequestStatus.EXPIRED);
        } else if (rentals.countOutstanding(c, requestId) == 0) {
            rentals.moveRequest(c, requestId, RentalRequestStatus.FILLED, RentalRequestStatus.COMPLETED);
        }
    }

    private void setReason(Connection c, long offerId, OfferStatus status, String reason) throws SQLException {
        try (var ps = c.prepareStatement(
                "UPDATE rental_offers SET status = ?, decline_reason = ? WHERE offer_id = ?")) {
            ps.setString(1, status.dbValue());
            ps.setString(2, reason);
            ps.setLong(3, offerId);
            ps.executeUpdate();
        }
    }

    private RentalRequest requireRequest(Connection c, long requestId) throws SQLException {
        return rentals.findRequest(c, requestId)
                .orElseThrow(() -> new ServiceException("This rental request no longer exists."));
    }

    private RentalOffer requireOffer(Connection c, long offerId) throws SQLException {
        return rentals.findOffer(c, offerId).orElseThrow(() -> new ServiceException("This offer no longer exists."));
    }

    private static void validate(RequestForm f, LocalDate today) {
        if (f.title() == null || f.title().isBlank()) {
            throw new ServiceException("Enter what the library needs, for example \"Portable projector\".");
        }
        if (f.purpose() == null || f.purpose().isBlank()) {
            throw new ServiceException("Enter the event or workshop the item is for.");
        }
        if (f.description() == null || f.description().isBlank()) {
            throw new ServiceException("Describe the item, so students know what fits.");
        }
        if (f.category() == null || f.minCondition() == null) {
            throw new ServiceException("Choose a category and the minimum condition.");
        }
        if (f.quantityNeeded() < 1 || f.quantityNeeded() > 20) {
            throw new ServiceException("The quantity must be between 1 and 20.");
        }
        if (f.offersCloseOn() == null || f.neededFrom() == null || f.neededUntil() == null) {
            throw new ServiceException("Choose the offer deadline and the rental dates.");
        }
        if (f.offersCloseOn().isBefore(today)) {
            throw new ServiceException("The offer deadline cannot be in the past.");
        }
        if (f.offersCloseOn().isAfter(f.neededFrom())) {
            throw new ServiceException("Offers must close on or before the day the library needs the item.");
        }
        if (f.neededUntil().isBefore(f.neededFrom())) {
            throw new ServiceException("The rental cannot end before it starts.");
        }
        if (f.rentalFee() == null || f.rentalFee().signum() <= 0) {
            throw new ServiceException("Enter what the library pays per item, for example 150.");
        }
        if (f.dropoffPoint() == null || f.dropoffPoint().isBlank()) {
            throw new ServiceException("Choose where students bring the item.");
        }
    }

    private static BigDecimal fee(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Values from the Create New Rental Request form. rentalFee is what the library pays per item, above zero. */
    public record RequestForm(
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

    /** Values from the Offer Item for Rent dialog. photo may be null. adultConfirmed: "I am 18 or older". */
    public record OfferForm(
            String itemName,
            String brandModel,
            String description,
            ItemCondition condition,
            Path photo,
            String ownerNote,
            boolean agreed,
            boolean adultConfirmed) {
    }
}
