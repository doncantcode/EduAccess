package com.eduaccess.model;

/** physical_items.category */
public enum ItemCategory implements DbCoded {
    CALCULATORS("Calculators"),
    LAPTOPS_TABLETS("Laptops & Tablets"),
    TEXTBOOKS("Textbooks"),
    LAB_STUDY_KITS("Lab/Study Kits");

    private final String dbValue;

    ItemCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    /** Textbooks are listed in the Physical Library. Everything else is equipment in the Physical Hub. */
    public Catalog catalog() {
        return this == TEXTBOOKS ? Catalog.LIBRARY : Catalog.HUB;
    }
}
