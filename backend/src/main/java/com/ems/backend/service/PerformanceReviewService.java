package com.ems.backend.service;

import com.ems.backend.dto.*;
import com.ems.backend.entity.*;
import com.ems.backend.exception.*;
import com.ems.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceReviewService {

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DepartmentRepository departmentRepository;

    // ── Reviews ────────────────────────────────────────────────────────────────

    @Transactional
    public PerformanceReviewResponse createReview(PerformanceReviewRequest req) {
        Employee employee = employeeRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.getEmployeeId()));
        Employee reviewer = employeeRepository.findById(req.getReviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found: " + req.getReviewerId()));

        if (reviewRepository.existsByEmployeeIdAndReviewPeriodAndReviewerId(
                req.getEmployeeId(), req.getReviewPeriod(), req.getReviewerId())) {
            throw new ReviewStateException("Review already exists for this employee/period/reviewer");
        }

        PerformanceReview review = PerformanceReview.builder()
                .employee(employee)
                .reviewer(reviewer)
                .reviewPeriod(req.getReviewPeriod())
                .rating(req.getRating())
                .comments(req.getComments())
                .goals(req.getGoals())
                .status(ReviewStatus.DRAFT)
                .build();

        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public PerformanceReviewResponse updateReview(Long id, PerformanceReviewRequest req) {
        PerformanceReview review = findReview(id);
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new ReviewStateException("Only DRAFT reviews can be updated");
        }
        review.setRating(req.getRating());
        review.setComments(req.getComments());
        review.setGoals(req.getGoals());
        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public PerformanceReviewResponse submitReview(Long id) {
        PerformanceReview review = findReview(id);
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new ReviewStateException("Only DRAFT reviews can be submitted");
        }
        review.setStatus(ReviewStatus.SUBMITTED);
        review.setSubmittedAt(LocalDateTime.now());
        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public PerformanceReviewResponse acknowledgeReview(Long id) {
        PerformanceReview review = findReview(id);
        if (review.getStatus() != ReviewStatus.SUBMITTED) {
            throw new ReviewStateException("Only SUBMITTED reviews can be acknowledged");
        }
        review.setStatus(ReviewStatus.ACKNOWLEDGED);
        review.setAcknowledgedAt(LocalDateTime.now());
        return toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public PerformanceReviewResponse getById(Long id) {
        return toResponse(findReview(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PerformanceReviewResponse> getByEmployee(Long employeeId, Pageable pageable) {
        Page<PerformanceReview> page = reviewRepository.findByEmployeeId(employeeId, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PerformanceReviewResponse> getByReviewer(Long reviewerId, Pageable pageable) {
        Page<PerformanceReview> page = reviewRepository.findByReviewerId(reviewerId, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PerformanceReviewResponse> getAll(Pageable pageable) {
        Page<PerformanceReview> page = reviewRepository.findAll(pageable);
        return toPagedResponse(page);
    }

    // ── Analytics ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalyticsSummary getAnalyticsSummary() {
        long totalEmployees = employeeRepository.count();
        long totalDepartments = departmentRepository.count();

        long pendingLeaves = leaveRequestRepository.countByStatus(
                com.ems.backend.entity.LeaveStatus.PENDING);
        long approvedLeavesThisYear = leaveRequestRepository.countByStatus(
                com.ems.backend.entity.LeaveStatus.APPROVED);

        // Payroll this month
        java.time.LocalDate now = java.time.LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();
        List<com.ems.backend.entity.PayrollRecord> payrolls =
                payrollRecordRepository.findAllByPayMonthAndPayYear(month, year);
        BigDecimal totalPayroll = payrolls.stream()
                .map(p -> p.getNetPay() != null ? p.getNetPay() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paidPayrolls = payrolls.stream()
                .filter(p -> p.getStatus() == com.ems.backend.entity.PayrollStatus.PAID)
                .count();

        // Performance
        Double avgRating = reviewRepository.count() > 0
                ? reviewRepository.ratingDistribution().stream()
                    .mapToDouble(r -> ((Number) r[0]).doubleValue() * ((Number) r[1]).longValue())
                    .sum() / reviewRepository.count()
                : null;
        long totalReviews = reviewRepository.count();

        Map<Integer, Long> ratingDist = new LinkedHashMap<>();
        for (Object[] row : reviewRepository.ratingDistribution()) {
            ratingDist.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        List<AnalyticsSummary.TopPerformerDto> topPerformers = reviewRepository
                .topPerformers(PageRequest.of(0, 5))
                .stream()
                .map(r -> new AnalyticsSummary.TopPerformerDto(
                        ((Number) r[0]).longValue(),
                        r[1] + " " + r[2],
                        r[3] != null ? ((Number) r[3]).doubleValue() : null))
                .collect(Collectors.toList());

        return new AnalyticsSummary(totalEmployees, totalDepartments,
                pendingLeaves, approvedLeavesThisYear,
                totalPayroll, paidPayrolls,
                avgRating, totalReviews, ratingDist, topPerformers);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PerformanceReview findReview(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private PerformanceReviewResponse toResponse(PerformanceReview r) {
        PerformanceReviewResponse resp = new PerformanceReviewResponse();
        resp.setId(r.getId());
        resp.setEmployeeId(r.getEmployee().getId());
        resp.setEmployeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName());
        resp.setReviewerId(r.getReviewer().getId());
        resp.setReviewerName(r.getReviewer().getFirstName() + " " + r.getReviewer().getLastName());
        resp.setReviewPeriod(r.getReviewPeriod());
        resp.setRating(r.getRating());
        resp.setComments(r.getComments());
        resp.setGoals(r.getGoals());
        resp.setStatus(r.getStatus());
        resp.setSubmittedAt(r.getSubmittedAt());
        resp.setAcknowledgedAt(r.getAcknowledgedAt());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }

    private PagedResponse<PerformanceReviewResponse> toPagedResponse(Page<PerformanceReview> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).collect(Collectors.toList()),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isLast());
    }
}
