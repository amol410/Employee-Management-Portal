package com.ems.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummary {
    // Workforce
    private long totalEmployees;
    private long totalDepartments;

    // Leave
    private long pendingLeaves;
    private long approvedLeavesThisYear;

    // Payroll
    private BigDecimal totalPayrollThisMonth;
    private long paidPayrollsThisMonth;

    // Performance
    private Double avgRatingOverall;
    private long totalReviews;
    private Map<Integer, Long> ratingDistribution;
    private List<TopPerformerDto> topPerformers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPerformerDto {
        private Long employeeId;
        private String employeeName;
        private Double avgRating;
    }
}
