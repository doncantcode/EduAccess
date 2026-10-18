package com.eduaccess.model;

import java.util.Arrays;
import java.util.List;

/**
 * Which tab a physical item is listed under. The Physical Library holds books and reading material, the Physical
 * Hub holds equipment. The split follows the item's category, so no column is needed: see {@link ItemCategory#catalog()}.
 */
public enum Catalog {
    LIBRARY,
    HUB;

    /** The categories listed under this catalog, in display order. */
    public List<ItemCategory> categories() {
        return Arrays.stream(ItemCategory.values()).filter(category -> category.catalog() == this).toList();
    }
}
