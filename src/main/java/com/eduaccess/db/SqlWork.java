package com.eduaccess.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of JDBC work that runs on a connection supplied by {@link DatabaseManager}. */
@FunctionalInterface
public interface SqlWork<T> {
    T apply(Connection connection) throws SQLException;
}
