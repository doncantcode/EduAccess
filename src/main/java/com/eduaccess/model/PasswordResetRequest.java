package com.eduaccess.model;

/** A student request to reset their account password. */
public record PasswordResetRequest(
        long requestId,
        long userId,
        String schoolId,
        String fullName,
        String requestedAt,
        String status
) {}
