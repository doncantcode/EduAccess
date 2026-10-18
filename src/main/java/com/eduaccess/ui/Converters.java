package com.eduaccess.ui;

import com.eduaccess.model.DbCoded;
import javafx.util.StringConverter;

/** Display text for enum values in combo boxes. */
final class Converters {

    private Converters() {
    }

    /** Shows an enum by its database text, for example "Like New" or "Books & Media". */
    static <E extends DbCoded> StringConverter<E> coded() {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : value.dbValue();
            }

            @Override
            public E fromString(String text) {
                return null;
            }
        };
    }
}
