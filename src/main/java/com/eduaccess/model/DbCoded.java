package com.eduaccess.model;

/** An enum constant stored in SQLite as the text value a CHECK constraint allows. */
public interface DbCoded {

    String dbValue();

    static <E extends Enum<E> & DbCoded> E fromDbValue(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.dbValue().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value);
    }
}
