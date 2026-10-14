package com.eduaccess.dao;

import com.eduaccess.model.DbCoded;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/** Small helpers for binding and reading nullable values with raw JDBC. */
final class Jdbc {

    private Jdbc() {
    }

    static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        setText(ps, index, value == null ? null : value.toString());
    }

    static void setCode(PreparedStatement ps, int index, DbCoded value) throws SQLException {
        setText(ps, index, value == null ? null : value.dbValue());
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : LocalDate.parse(text);
    }

    static <E extends Enum<E> & DbCoded> E code(ResultSet rs, String column, Class<E> type) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : DbCoded.fromDbValue(type, text);
    }

    static BigDecimal money(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) != 0;
    }

    /** SQLite's driver reports integer columns as Integer through getObject, so read them with getLong. */
    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
