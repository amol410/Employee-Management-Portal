package com.ems.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type", "leave_year"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "leave_year", nullable = false)
    private Integer year;

    @Column(name = "total_days", nullable = false)
    @Builder.Default
    private Integer totalDays = 0;

    @Column(name = "used_days", nullable = false)
    @Builder.Default
    private Integer usedDays = 0;

    @Column(name = "pending_days", nullable = false)
    @Builder.Default
    private Integer pendingDays = 0;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public int getRemainingDays() {
        return totalDays - usedDays - pendingDays;
    }
}
