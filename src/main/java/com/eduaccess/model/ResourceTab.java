package com.eduaccess.model;

/** digital_resources.category_tab */
public enum ResourceTab implements DbCoded {
    K12_MODULE_HUB("K-12 Module Hub"),
    COLLEGE_REPOSITORY("College Repository");

    private final String dbValue;

    ResourceTab(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
