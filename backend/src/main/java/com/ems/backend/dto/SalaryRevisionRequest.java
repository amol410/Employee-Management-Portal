package com.ems.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalaryRevisionRequest {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "New salary is required")
    @DecimalMin(value = "0.0", message = "Salary must be >= 0")
    private BigDecimal newSalary;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private String reason;

    private Long revisedById;
}
