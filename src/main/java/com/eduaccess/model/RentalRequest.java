package com.eduaccess.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Something the library wants to rent from students. approvedCount counts offers that are Approved, Checked-In or
 * Returned, pendingCount counts offers still waiting for a staff decision.
 */
public record RentalRequest(
        long id,
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
        String dropoffPoint,
        RentalRequestStatus status,
        int approvedCount,
        int pendingCount) {

    /** Days the library keeps the item, counting both ends. */
    public long rentalDays() {
        return ChronoUnit.DAYS.between(neededFrom, neededUntil) + 1;
    }

    public boolean acceptsOffers(LocalDate today) {
        return status == RentalRequestStatus.OPEN && !today.isAfter(offersCloseOn);
    }
}
