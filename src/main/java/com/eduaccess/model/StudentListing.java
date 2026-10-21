package com.eduaccess.model;

import java.math.BigDecimal;

/** A student's item posted for peer-to-peer lending in the Student Marketplace. */
public record StudentListing(
        long id,
        long ownerId,
        String title,
        String description,
        MarketplaceCategory category,
        ItemCondition condition,
        EducationLevel educationLevel,
        BigDecimal dailyFee,
        String pickupPoint,
        String photoPath,
        String status,
        String createdAt
) {}
