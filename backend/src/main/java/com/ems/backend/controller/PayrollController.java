package com.ems.backend.controller;

import com.ems.backend.dto.*;
import com.ems.backend.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    // ── Payroll Records ────────────────────────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PayrollResponse> generate(@Valid @RequestBody PayrollRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.generatePayroll(dto));
    }

    @PatchMapping("/{id}/process")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PayrollResponse> process(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.processPayroll(id));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PayrollResponse> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.markPaid(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayrollResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PagedResponse<PayrollResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("payYear").descending().and(Sort.by("payMonth").descending()));
        if (month != null && year != null) {
            return ResponseEntity.ok(payrollService.getByMonthYear(month, year, pageable));
        }
        return ResponseEntity.ok(payrollService.getAll(pageable));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<PagedResponse<PayrollResponse>> getByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("payYear").descending().and(Sort.by("payMonth").descending()));
        return ResponseEntity.ok(payrollService.getByEmployee(employeeId, pageable));
    }

    // ── Salary Revisions ───────────────────────────────────────────────────────

    @PostMapping("/salary-revisions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SalaryRevisionResponse> reviseSalary(@Valid @RequestBody SalaryRevisionRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.reviseSalary(dto));
    }

    @GetMapping("/salary-revisions")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PagedResponse<SalaryRevisionResponse>> getAllRevisions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(payrollService.getAllRevisions(pageable));
    }

    @GetMapping("/salary-revisions/employee/{employeeId}")
    public ResponseEntity<PagedResponse<SalaryRevisionResponse>> getRevisionsByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(payrollService.getRevisionsByEmployee(employeeId, pageable));
    }
}
