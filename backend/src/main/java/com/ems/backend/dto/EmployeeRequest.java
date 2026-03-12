package com.ems.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    private String phone;

    private LocalDate hireDate;

    private String position;

    @DecimalMin(value = "0.0", message = "Salary must be non-negative")
    private BigDecimal salary;

    private Long departmentId;

    private Long managerId;
}
