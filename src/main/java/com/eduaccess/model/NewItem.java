package com.eduaccess.model;

import java.util.List;

/** Values for a new catalog entry, such as a donation waiting for review. */
public record NewItem(
        Long donorId,
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
