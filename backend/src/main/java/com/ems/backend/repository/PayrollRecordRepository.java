package com.ems.backend.repository;

import com.ems.backend.entity.PayrollRecord;
import com.ems.backend.entity.PayrollStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {
    Optional<PayrollRecord> findByEmployeeIdAndPayMonthAndPayYear(Long employeeId, int month, int year);
    Page<PayrollRecord> findByEmployeeId(Long employeeId, Pageable pageable);
    Page<PayrollRecord> findByPayMonthAndPayYear(int month, int year, Pageable pageable);
    Page<PayrollRecord> findByStatus(PayrollStatus status, Pageable pageable);
    boolean existsByEmployeeIdAndPayMonthAndPayYear(Long employeeId, int month, int year);
}
