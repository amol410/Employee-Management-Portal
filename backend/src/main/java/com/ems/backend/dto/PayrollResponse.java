package com.ems.backend.dto;

import com.ems.backend.entity.PayrollStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayrollResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private Integer payMonth;
    private Integer payYear;
    private BigDecimal basicSalary;
    private BigDecimal allowances;
    private BigDecimal deductions;
    private BigDecimal netPay;
    private String notes;
    private PayrollStatus status;
    private LocalDateTime processedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
