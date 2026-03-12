package com.ems.backend.service;

import com.ems.backend.dto.EmployeeRequest;
import com.ems.backend.dto.EmployeeResponse;
import com.ems.backend.dto.PagedResponse;
import com.ems.backend.entity.Employee;
import com.ems.backend.exception.DuplicateEmailException;
import com.ems.backend.exception.ResourceNotFoundException;
import com.ems.backend.mapper.EmployeeMapper;
import com.ems.backend.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeMapper employeeMapper;
    @InjectMocks EmployeeService employeeService;

    private Employee employee;
    private EmployeeRequest request;
    private EmployeeResponse response;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id(1L).firstName("John").lastName("Doe")
                .email("john@example.com").salary(BigDecimal.valueOf(50000))
                .build();

        request = new EmployeeRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setSalary(BigDecimal.valueOf(50000));

        response = new EmployeeResponse();
        response.setId(1L);
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setEmail("john@example.com");

        pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
    }

    @Test
    void getAllEmployees_returnsPagedResponse() {
        Page<Employee> page = new PageImpl<>(List.of(employee), pageable, 1);
        when(employeeRepository.findAll(pageable)).thenReturn(page);
        when(employeeMapper.toResponse(employee)).thenReturn(response);

        PagedResponse<EmployeeResponse> result = employeeService.getAllEmployees(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getEmployeeById_found_returnsResponse() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeMapper.toResponse(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.getEmployeeById(1L);

        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getEmployeeById_notFound_throwsException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createEmployee_success() {
        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toResponse(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.createEmployee(request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(employeeRepository).save(employee);
    }

    @Test
    void createEmployee_duplicateEmail_throwsException() {
        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateEmployee_success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toResponse(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.updateEmployee(1L, request);

        assertThat(result).isNotNull();
        verify(employeeMapper).updateEntityFromRequest(request, employee);
    }

    @Test
    void deleteEmployee_success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(1L);

        verify(employeeRepository).deleteById(1L);
    }

    @Test
    void deleteEmployee_notFound_throwsException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deleteEmployee(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(employeeRepository, never()).deleteById(any());
    }
}
