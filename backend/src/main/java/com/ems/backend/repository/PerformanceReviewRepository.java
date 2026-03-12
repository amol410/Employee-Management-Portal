package com.ems.backend.repository;

import com.ems.backend.entity.PerformanceReview;
import com.ems.backend.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    Page<PerformanceReview> findByEmployeeId(Long employeeId, Pageable pageable);
    Page<PerformanceReview> findByReviewerId(Long reviewerId, Pageable pageable);
    boolean existsByEmployeeIdAndReviewPeriodAndReviewerId(Long employeeId, String period, Long reviewerId);

    // Analytics
    @Query("SELECT AVG(r.rating) FROM PerformanceReview r WHERE r.employee.id = :empId")
    Double avgRatingByEmployee(@Param("empId") Long empId);

    @Query("SELECT AVG(r.rating) FROM PerformanceReview r WHERE r.employee.department.id = :deptId")
    Double avgRatingByDepartment(@Param("deptId") Long deptId);

    @Query("SELECT r.employee.id, r.employee.firstName, r.employee.lastName, AVG(r.rating) as avg " +
           "FROM PerformanceReview r GROUP BY r.employee.id, r.employee.firstName, r.employee.lastName " +
           "ORDER BY avg DESC")
    List<Object[]> topPerformers(Pageable pageable);

    @Query("SELECT COUNT(r) FROM PerformanceReview r WHERE r.status = :status")
    long countByStatus(@Param("status") ReviewStatus status);

    @Query("SELECT r.rating, COUNT(r) FROM PerformanceReview r GROUP BY r.rating ORDER BY r.rating")
    List<Object[]> ratingDistribution();
}
