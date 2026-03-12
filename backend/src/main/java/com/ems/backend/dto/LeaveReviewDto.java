package com.ems.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeaveReviewDto {

    @NotNull(message = "Reviewer employee ID is required")
    private Long reviewedById;

    private String rejectionReason;
}
