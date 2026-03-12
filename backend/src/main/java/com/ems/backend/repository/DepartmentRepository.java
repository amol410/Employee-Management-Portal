package com.ems.backend.repository;

import com.ems.backend.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByName(String name);

    Optional<Department> findByName(String name);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :deptId")
    long countEmployeesByDepartmentId(@Param("deptId") Long deptId);
}
