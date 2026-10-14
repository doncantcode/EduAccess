package com.eduaccess.model;

import java.util.List;

/** A catalog entry. donorId and donorName are null for library-owned stock. */
public record PhysicalItem(
        long id,
        Long donorId,
        String donorName,
        String title,
        String brandModel,
        ItemCategory category,
        ItemCondition condition,
        String pickupPoint,
        String photoPath,
        ItemStatus status,
        GivingType givingType,
        List<EducationLevel> levels) {
}
