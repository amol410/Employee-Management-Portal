package com.ems.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PerformanceReviewRequest {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Reviewer ID is required")
    private Long reviewerId;

    @NotBlank(message = "Review period is required")
    private String reviewPeriod;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be 1–5")
    @Max(value = 5, message = "Rating must be 1–5")
    private Integer rating;

    @Size(max = 2000)
    private String comments;

    @Size(max = 2000)
    private String goals;
}
