package com.ems.backend.exception;

public class DepartmentHasEmployeesException extends RuntimeException {
    public DepartmentHasEmployeesException(String message) {
        super(message);
    }
}
