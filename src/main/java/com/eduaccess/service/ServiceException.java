package com.eduaccess.service;

/** A rule was broken or the database failed. The message is written for the user to read. */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
