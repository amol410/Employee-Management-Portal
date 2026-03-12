package com.ems.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate hireDate;
    private String position;
    private BigDecimal salary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
