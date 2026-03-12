package com.ems.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollRequest {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Pay month is required")
    @Min(value = 1, message = "Month must be 1-12")
    @Max(value = 12, message = "Month must be 1-12")
    private Integer payMonth;

    @NotNull(message = "Pay year is required")
    @Min(value = 2000, message = "Year must be >= 2000")
    private Integer payYear;

    private BigDecimal allowances;
    private BigDecimal deductions;
    private String notes;
}
