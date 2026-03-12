package com.ems.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DepartmentResponse {
    private Long id;
    private String name;
    private String description;
    private long employeeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
