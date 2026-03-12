package com.ems.backend.controller;

import com.ems.backend.dto.*;
import com.ems.backend.service.PerformanceReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final PerformanceReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> create(@Valid @RequestBody PerformanceReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody PerformanceReviewRequest req) {
        return ResponseEntity.ok(reviewService.updateReview(id, req));
    }

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> submit(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.submitReview(id));
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<PerformanceReviewResponse> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.acknowledgeReview(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerformanceReviewResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PagedResponse<PerformanceReviewResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(reviewService.getAll(pageable));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<PagedResponse<PerformanceReviewResponse>> getByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(reviewService.getByEmployee(employeeId, pageable));
    }

    @GetMapping("/reviewer/{reviewerId}")
    public ResponseEntity<PagedResponse<PerformanceReviewResponse>> getByReviewer(
            @PathVariable Long reviewerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(reviewService.getByReviewer(reviewerId, pageable));
    }
}
