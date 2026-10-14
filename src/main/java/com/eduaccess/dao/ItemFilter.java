package com.eduaccess.dao;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import java.util.List;

/**
 * Catalog filter. Empty categories means all categories, a null level means all levels, and a null status
 * means every status except Pending and Rejected, which students never see.
 */
public record ItemFilter(List<ItemCategory> categories, EducationLevel level, ItemStatus status, String search) {

    public ItemFilter {
        categories = categories == null ? List.of() : List.copyOf(categories);
        search = search == null || search.isBlank() ? null : search.strip();
    }

    public static ItemFilter published() {
        return new ItemFilter(List.of(), null, null, null);
    }
}
