package com.ems.backend.repository;

import com.ems.backend.entity.SalaryRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryRevisionRepository extends JpaRepository<SalaryRevision, Long> {
    Page<SalaryRevision> findByEmployeeId(Long employeeId, Pageable pageable);
}
