package com.eduaccess.model;

import java.time.LocalDateTime;
import java.util.List;

/** A saved, unsent donation form. Any field may be null, because a draft can be incomplete. */
public record DonationDraft(
        ItemCategory category,
        String title,
        String brandModel,
        ItemCondition condition,
        List<EducationLevel> levels,
        String dropoffPoint,
        String photoPath,
        LocalDateTime savedAt) {
}
