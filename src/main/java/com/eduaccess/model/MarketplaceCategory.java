package com.eduaccess.model;

/** Categories for the Student Marketplace. */
public enum MarketplaceCategory implements DbCoded {
    CALCULATORS("Calculators"),
    LAPTOPS("Laptops & Tablets"),
    TEXTBOOKS("Textbooks"),
    LAB_KITS("Lab/Study Kits"),
    OTHER("Other");

    private final String dbValue;

    MarketplaceCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
