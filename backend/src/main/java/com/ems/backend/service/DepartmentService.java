package com.ems.backend.service;

import com.ems.backend.dto.DepartmentRequest;
import com.ems.backend.dto.DepartmentResponse;
import com.ems.backend.entity.Department;
import com.ems.backend.exception.DepartmentHasEmployeesException;
import com.ems.backend.exception.DuplicateEmailException;
import com.ems.backend.exception.ResourceNotFoundException;
import com.ems.backend.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new DuplicateEmailException("Department name already exists: " + request.getName());
        }
        Department dept = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toResponse(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department dept = findOrThrow(id);
        if (!dept.getName().equals(request.getName())
                && departmentRepository.existsByName(request.getName())) {
            throw new DuplicateEmailException("Department name already exists: " + request.getName());
        }
        dept.setName(request.getName());
        dept.setDescription(request.getDescription());
        return toResponse(departmentRepository.save(dept));
    }

    @Transactional
    public void deleteDepartment(Long id) {
        findOrThrow(id);
        long count = departmentRepository.countEmployeesByDepartmentId(id);
        if (count > 0) {
            throw new DepartmentHasEmployeesException(
                    "Cannot delete department with " + count + " active employee(s). Reassign them first.");
        }
        departmentRepository.deleteById(id);
    }

    private Department findOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }

    private DepartmentResponse toResponse(Department dept) {
        long count = departmentRepository.countEmployeesByDepartmentId(dept.getId());
        DepartmentResponse response = new DepartmentResponse();
        response.setId(dept.getId());
        response.setName(dept.getName());
        response.setDescription(dept.getDescription());
        response.setEmployeeCount(count);
        response.setCreatedAt(dept.getCreatedAt());
        response.setUpdatedAt(dept.getUpdatedAt());
        return response;
    }
}
