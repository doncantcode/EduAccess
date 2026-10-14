package com.eduaccess.service;

import com.eduaccess.db.DatabaseManager;
import com.eduaccess.db.SqlWork;
import java.sql.SQLException;

/** Runs JDBC work for the services and turns checked SQL errors into {@link ServiceException}. */
final class Sql {

    private Sql() {
    }

    static <T> T read(DatabaseManager db, SqlWork<T> work) {
        try {
            return db.read(work);
        } catch (SQLException e) {
            throw new ServiceException("Database error: " + e.getMessage(), e);
        }
    }

    /** Runs the work in one transaction. A ServiceException thrown inside rolls everything back. */
    static <T> T write(DatabaseManager db, SqlWork<T> work) {
        try {
            return db.transaction(work);
        } catch (SQLException e) {
            throw new ServiceException("Database error: " + e.getMessage(), e);
        }
    }
}
