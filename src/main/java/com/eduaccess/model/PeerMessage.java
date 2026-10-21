package com.eduaccess.model;

/** A chat message between students concerning a marketplace listing. */
public record PeerMessage(
        long id,
        long listingId,
        long senderId,
        String body,
        String sentAt
) {}
