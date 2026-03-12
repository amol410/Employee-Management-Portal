package com.ems.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SalaryRevisionResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private BigDecimal oldSalary;
    private BigDecimal newSalary;
    private BigDecimal changeAmount;
    private Double changePercent;
    private LocalDate effectiveDate;
    private String reason;
    private Long revisedById;
    private String revisedByName;
    private LocalDateTime createdAt;
}
