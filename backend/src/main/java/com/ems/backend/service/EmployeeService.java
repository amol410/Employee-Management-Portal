package com.ems.backend.service;

import com.ems.backend.dto.EmployeeRequest;
import com.ems.backend.dto.EmployeeResponse;
import com.ems.backend.dto.PagedResponse;
import com.ems.backend.entity.Employee;
import com.ems.backend.exception.DuplicateEmailException;
import com.ems.backend.exception.ResourceNotFoundException;
import com.ems.backend.mapper.EmployeeMapper;
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
    private final EmployeeMapper employeeMapper;

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> getAllEmployees(String search, Pageable pageable) {
        Page<Employee> page;
        if (search != null && !search.isBlank()) {
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
        Employee saved = employeeRepository.save(employeeMapper.toEntity(request));
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        Employee employee = findOrThrow(id);
        if (!employee.getEmail().equals(request.getEmail())
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already in use: " + request.getEmail());
        }
        employeeMapper.updateEntityFromRequest(request, employee);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public void deleteEmployee(Long id) {
        findOrThrow(id);
        employeeRepository.deleteById(id);
    }

    private Employee findOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }
}
