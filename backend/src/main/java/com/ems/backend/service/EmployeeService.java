package com.ems.backend.service;

import com.ems.backend.dto.EmployeeRequest;
import com.ems.backend.dto.EmployeeResponse;
import com.ems.backend.dto.PagedResponse;
import com.ems.backend.entity.Department;
import com.ems.backend.entity.Employee;
import com.ems.backend.exception.DuplicateEmailException;
import com.ems.backend.exception.ResourceNotFoundException;
import com.ems.backend.mapper.EmployeeMapper;
import com.ems.backend.repository.DepartmentRepository;
import com.ems.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> getAllEmployees(String search, Long departmentId, Pageable pageable) {
        Page<Employee> page;
        if (departmentId != null) {
            page = employeeRepository.findByDepartmentId(departmentId, pageable);
        } else if (search != null && !search.isBlank()) {
            page = employeeRepository.searchEmployees(search.trim(), pageable);
        } else {
            page = employeeRepository.findAll(pageable);
        }
        return new PagedResponse<>(
                page.getContent().stream().map(employeeMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        return employeeMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already in use: " + request.getEmail());
        }
        Employee employee = employeeMapper.toEntity(request);
        resolveRelationships(employee, request);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        Employee employee = findOrThrow(id);
        if (!employee.getEmail().equals(request.getEmail())
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already in use: " + request.getEmail());
        }
        employeeMapper.updateEntityFromRequest(request, employee);
        resolveRelationships(employee, request);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public void deleteEmployee(Long id) {
        findOrThrow(id);
        employeeRepository.deleteById(id);
    }

    private void resolveRelationships(Employee employee, EmployeeRequest request) {
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with id: " + request.getDepartmentId()));
            employee.setDepartment(dept);
        } else {
            employee.setDepartment(null);
        }

        if (request.getManagerId() != null) {
            if (request.getManagerId().equals(employee.getId())) {
                throw new IllegalArgumentException("Employee cannot be their own manager");
            }
            Employee manager = findOrThrow(request.getManagerId());
            employee.setManager(manager);
        } else {
            employee.setManager(null);
        }
    }

    private Employee findOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }
}
