package com.eduaccess.model;

import java.math.BigDecimal;

/** A peer borrow request between students. */
public record PeerBorrow(
        long id,
        long listingId,
        long borrowerId,
        long ownerId,
        PeerBorrowStatus status,
        BigDecimal agreedFee,
        String note,
        String requestedAt,
        String respondedAt,
        String borrowedAt,
        String returnedAt
) {
    public boolean isLive() {
        return status == PeerBorrowStatus.REQUESTED
                || status == PeerBorrowStatus.ACCEPTED
                || status == PeerBorrowStatus.BORROWED;
    }
}
