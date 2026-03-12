package com.ems.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "review_period", "reviewer_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee reviewer;

    // e.g. "2026-Q1", "2025-Annual"
    @Column(name = "review_period", nullable = false)
    private String reviewPeriod;

    // 1–5 rating
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comments", length = 2000)
    private String comments;

    @Column(name = "goals", length = 2000)
    private String goals;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
