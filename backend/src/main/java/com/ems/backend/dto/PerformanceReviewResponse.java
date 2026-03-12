package com.ems.backend.dto;

import com.ems.backend.entity.ReviewStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PerformanceReviewResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewPeriod;
    private Integer rating;
    private String comments;
    private String goals;
    private ReviewStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime createdAt;
}
