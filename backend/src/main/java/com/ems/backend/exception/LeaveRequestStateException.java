package com.ems.backend.exception;

public class LeaveRequestStateException extends RuntimeException {
    public LeaveRequestStateException(String message) {
        super(message);
    }
}
