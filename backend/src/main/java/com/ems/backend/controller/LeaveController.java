package com.ems.backend.controller;

import com.ems.backend.dto.*;
import com.ems.backend.entity.LeaveStatus;
import com.ems.backend.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    // ── Apply (any authenticated employee) ────────────────────────────────────
    @PostMapping("/apply")
    public ResponseEntity<LeaveRequestResponse> apply(@Valid @RequestBody LeaveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.applyLeave(dto));
    }

    // ── Cancel own request ─────────────────────────────────────────────────────
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<LeaveRequestResponse> cancel(
            @PathVariable Long id,
            @RequestParam Long employeeId) {
        return ResponseEntity.ok(leaveService.cancelLeave(id, employeeId));
    }

    // ── Approve / Reject — ADMIN or MANAGER ───────────────────────────────────
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<LeaveRequestResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody LeaveReviewDto reviewDto) {
        return ResponseEntity.ok(leaveService.approveLeave(id, reviewDto));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<LeaveRequestResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody LeaveReviewDto reviewDto) {
        return ResponseEntity.ok(leaveService.rejectLeave(id, reviewDto));
    }

    // ── Read endpoints ─────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
    public ResponseEntity<PagedResponse<LeaveRequestResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LeaveStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) {
            return ResponseEntity.ok(leaveService.getByStatus(status, pageable));
        }
        return ResponseEntity.ok(leaveService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<PagedResponse<LeaveRequestResponse>> getByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(leaveService.getByEmployee(employeeId, pageable));
    }

    // ── Leave Balance ──────────────────────────────────────────────────────────
    @PostMapping("/balances")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<LeaveBalanceResponse> setBalance(@Valid @RequestBody LeaveBalanceRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.setBalance(dto));
    }

    @GetMapping("/balances/{employeeId}")
    public ResponseEntity<List<LeaveBalanceResponse>> getBalances(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        return ResponseEntity.ok(leaveService.getBalances(employeeId, year));
    }
}
