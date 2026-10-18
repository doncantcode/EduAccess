package com.eduaccess.model;

import java.math.BigDecimal;

/** A student's offer to rent an item to the library, joined with the owner and the request title for display. */
public record RentalOffer(
        long id,
        long requestId,
        String requestTitle,
        long ownerId,
        String ownerName,
        String ownerSchoolId,
        String itemName,
        String brandModel,
        String description,
        ItemCondition condition,
        String photoPath,
        String ownerNote,
        OfferStatus status,
        BigDecimal agreedFee,
        boolean feePaid,
        String declineReason,
        ItemCondition conditionIn,
        ItemCondition conditionOut,
        String damageNote) {

    /** Offers that still hold a place on the request: waiting, approved, or with the library. */
    public boolean isLive() {
        return status == OfferStatus.OFFERED || status == OfferStatus.APPROVED || status == OfferStatus.CHECKED_IN;
    }
}
