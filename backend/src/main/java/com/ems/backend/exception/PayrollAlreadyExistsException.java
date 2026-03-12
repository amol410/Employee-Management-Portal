package com.ems.backend.exception;

public class PayrollAlreadyExistsException extends RuntimeException {
    public PayrollAlreadyExistsException(String message) {
        super(message);
    }
}
