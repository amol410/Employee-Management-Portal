package com.ems.backend.service;

import com.ems.backend.dto.*;
import com.ems.backend.entity.*;
import com.ems.backend.exception.*;
import com.ems.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRecordRepository payrollRecordRepository;
    private final SalaryRevisionRepository salaryRevisionRepository;
    private final EmployeeRepository employeeRepository;

    // ── Payroll ────────────────────────────────────────────────────────────────

    @Transactional
    public PayrollResponse generatePayroll(PayrollRequest dto) {
        Employee employee = findEmployee(dto.getEmployeeId());

        if (payrollRecordRepository.existsByEmployeeIdAndPayMonthAndPayYear(
                dto.getEmployeeId(), dto.getPayMonth(), dto.getPayYear())) {
            throw new PayrollAlreadyExistsException(
                    "Payroll already exists for employee " + dto.getEmployeeId()
                            + " for " + dto.getPayMonth() + "/" + dto.getPayYear());
        }

        BigDecimal basic = employee.getSalary() != null ? employee.getSalary() : BigDecimal.ZERO;
        BigDecimal allowances = dto.getAllowances() != null ? dto.getAllowances() : BigDecimal.ZERO;
        BigDecimal deductions = dto.getDeductions() != null ? dto.getDeductions() : BigDecimal.ZERO;
        BigDecimal netPay = basic.add(allowances).subtract(deductions);

        PayrollRecord record = PayrollRecord.builder()
                .employee(employee)
                .payMonth(dto.getPayMonth())
                .payYear(dto.getPayYear())
                .basicSalary(basic)
                .allowances(allowances)
                .deductions(deductions)
                .netPay(netPay)
                .notes(dto.getNotes())
                .status(PayrollStatus.DRAFT)
                .build();

        return toResponse(payrollRecordRepository.save(record));
    }

    @Transactional
    public PayrollResponse processPayroll(Long id) {
        PayrollRecord record = findRecord(id);
        if (record.getStatus() != PayrollStatus.DRAFT) {
            throw new PayrollStateException("Only DRAFT payrolls can be processed");
        }
        record.setStatus(PayrollStatus.PROCESSED);
        record.setProcessedAt(LocalDateTime.now());
        return toResponse(payrollRecordRepository.save(record));
    }

    @Transactional
    public PayrollResponse markPaid(Long id) {
        PayrollRecord record = findRecord(id);
        if (record.getStatus() != PayrollStatus.PROCESSED) {
            throw new PayrollStateException("Only PROCESSED payrolls can be marked as PAID");
        }
        record.setStatus(PayrollStatus.PAID);
        record.setPaidAt(LocalDateTime.now());
        return toResponse(payrollRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public PayrollResponse getById(Long id) {
        return toResponse(findRecord(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayrollResponse> getAll(Pageable pageable) {
        return toPagedResponse(payrollRecordRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayrollResponse> getByEmployee(Long employeeId, Pageable pageable) {
        findEmployee(employeeId);
        return toPagedResponse(payrollRecordRepository.findByEmployeeId(employeeId, pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayrollResponse> getByMonthYear(int month, int year, Pageable pageable) {
        return toPagedResponse(payrollRecordRepository.findByPayMonthAndPayYear(month, year, pageable));
    }

    // ── Salary Revisions ───────────────────────────────────────────────────────

    @Transactional
    public SalaryRevisionResponse reviseSalary(SalaryRevisionRequest dto) {
        Employee employee = findEmployee(dto.getEmployeeId());

        BigDecimal oldSalary = employee.getSalary() != null ? employee.getSalary() : BigDecimal.ZERO;
        BigDecimal newSalary = dto.getNewSalary();

        SalaryRevision revision = SalaryRevision.builder()
                .employee(employee)
                .oldSalary(oldSalary)
                .newSalary(newSalary)
                .effectiveDate(dto.getEffectiveDate())
                .reason(dto.getReason())
                .build();

        if (dto.getRevisedById() != null) {
            revision.setRevisedBy(findEmployee(dto.getRevisedById()));
        }

        employee.setSalary(newSalary);
        employeeRepository.save(employee);

        return toRevisionResponse(salaryRevisionRepository.save(revision));
    }

    @Transactional(readOnly = true)
    public PagedResponse<SalaryRevisionResponse> getRevisionsByEmployee(Long employeeId, Pageable pageable) {
        findEmployee(employeeId);
        return new PagedResponse<>(
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable)
                        .getContent().stream().map(this::toRevisionResponse).toList(),
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable).getNumber(),
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable).getSize(),
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable).getTotalElements(),
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable).getTotalPages(),
                salaryRevisionRepository.findByEmployeeId(employeeId, pageable).isLast()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<SalaryRevisionResponse> getAllRevisions(Pageable pageable) {
        Page<SalaryRevision> page = salaryRevisionRepository.findAll(pageable);
        return new PagedResponse<>(
                page.getContent().stream().map(this::toRevisionResponse).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Employee findEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private PayrollRecord findRecord(Long id) {
        return payrollRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record not found with id: " + id));
    }

    private PayrollResponse toResponse(PayrollRecord r) {
        PayrollResponse resp = new PayrollResponse();
        resp.setId(r.getId());
        resp.setEmployeeId(r.getEmployee().getId());
        resp.setEmployeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName());
        resp.setDepartmentName(r.getEmployee().getDepartment() != null
                ? r.getEmployee().getDepartment().getName() : null);
        resp.setPayMonth(r.getPayMonth());
        resp.setPayYear(r.getPayYear());
        resp.setBasicSalary(r.getBasicSalary());
        resp.setAllowances(r.getAllowances());
        resp.setDeductions(r.getDeductions());
        resp.setNetPay(r.getNetPay());
        resp.setNotes(r.getNotes());
        resp.setStatus(r.getStatus());
        resp.setProcessedAt(r.getProcessedAt());
        resp.setPaidAt(r.getPaidAt());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }

    private SalaryRevisionResponse toRevisionResponse(SalaryRevision r) {
        SalaryRevisionResponse resp = new SalaryRevisionResponse();
        resp.setId(r.getId());
        resp.setEmployeeId(r.getEmployee().getId());
        resp.setEmployeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName());
        resp.setOldSalary(r.getOldSalary());
        resp.setNewSalary(r.getNewSalary());
        resp.setChangeAmount(r.getNewSalary().subtract(r.getOldSalary()));
        if (r.getOldSalary().compareTo(BigDecimal.ZERO) != 0) {
            double pct = r.getNewSalary().subtract(r.getOldSalary())
                    .divide(r.getOldSalary(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            resp.setChangePercent(Math.round(pct * 100.0) / 100.0);
        }
        resp.setEffectiveDate(r.getEffectiveDate());
        resp.setReason(r.getReason());
        resp.setCreatedAt(r.getCreatedAt());
        if (r.getRevisedBy() != null) {
            resp.setRevisedById(r.getRevisedBy().getId());
            resp.setRevisedByName(r.getRevisedBy().getFirstName() + " " + r.getRevisedBy().getLastName());
        }
        return resp;
    }

    private PagedResponse<PayrollResponse> toPagedResponse(Page<PayrollRecord> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}
