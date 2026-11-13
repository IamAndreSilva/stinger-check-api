package com.stingercheck.common;

import java.sql.SQLException;

/** Looks through an exception's causes for the PostgreSQL error code (SQLState) behind it. */
public final class SqlStates {

    public static final String UNIQUE_VIOLATION = "23505";
    public static final String LOCK_NOT_AVAILABLE = "55P03";

    private SqlStates() {}

    public static boolean has(Throwable error, String sqlState) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
