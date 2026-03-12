package com.ems.backend.controller;

import com.ems.backend.dto.AnalyticsSummary;
import com.ems.backend.service.PerformanceReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final PerformanceReviewService reviewService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<AnalyticsSummary> getSummary() {
        return ResponseEntity.ok(reviewService.getAnalyticsSummary());
    }
}
