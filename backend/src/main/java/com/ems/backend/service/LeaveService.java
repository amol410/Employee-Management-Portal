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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;

    // ── Leave Requests ─────────────────────────────────────────────────────────

    @Transactional
    public LeaveRequestResponse applyLeave(LeaveRequestDto dto) {
        Employee employee = findEmployee(dto.getEmployeeId());

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        int days = countWorkingDays(dto.getStartDate(), dto.getEndDate());
        if (days == 0) {
            throw new IllegalArgumentException("Leave period contains no working days");
        }

        // Check balance (skip for UNPAID)
        if (dto.getLeaveType() != LeaveType.UNPAID) {
            int year = dto.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeAndYear(dto.getEmployeeId(), dto.getLeaveType(), year)
                    .orElseThrow(() -> new InsufficientLeaveBalanceException(
                            "No leave balance configured for " + dto.getLeaveType() + " in " + year));

            if (balance.getRemainingDays() < days) {
                throw new InsufficientLeaveBalanceException(
                        "Insufficient balance. Requested: " + days + ", Available: " + balance.getRemainingDays());
            }
            balance.setPendingDays(balance.getPendingDays() + days);
            leaveBalanceRepository.save(balance);
        }

        LeaveRequest request = LeaveRequest.builder()
                .employee(employee)
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .daysRequested(days)
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        return toResponse(leaveRequestRepository.save(request));
    }

    @Transactional
    public LeaveRequestResponse approveLeave(Long requestId, LeaveReviewDto reviewDto) {
        LeaveRequest request = findRequest(requestId);
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveRequestStateException("Only PENDING requests can be approved");
        }

        Employee reviewer = findEmployee(reviewDto.getReviewedById());
        request.setStatus(LeaveStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());

        // Move pending → used
        if (request.getLeaveType() != LeaveType.UNPAID) {
            int year = request.getStartDate().getYear();
            leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeAndYear(
                            request.getEmployee().getId(), request.getLeaveType(), year)
                    .ifPresent(b -> {
                        b.setPendingDays(Math.max(0, b.getPendingDays() - request.getDaysRequested()));
                        b.setUsedDays(b.getUsedDays() + request.getDaysRequested());
                        leaveBalanceRepository.save(b);
                    });
        }

        return toResponse(leaveRequestRepository.save(request));
    }

    @Transactional
    public LeaveRequestResponse rejectLeave(Long requestId, LeaveReviewDto reviewDto) {
        LeaveRequest request = findRequest(requestId);
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveRequestStateException("Only PENDING requests can be rejected");
        }

        Employee reviewer = findEmployee(reviewDto.getReviewedById());
        request.setStatus(LeaveStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(reviewDto.getRejectionReason());

        // Release pending days
        if (request.getLeaveType() != LeaveType.UNPAID) {
            int year = request.getStartDate().getYear();
            leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeAndYear(
                            request.getEmployee().getId(), request.getLeaveType(), year)
                    .ifPresent(b -> {
                        b.setPendingDays(Math.max(0, b.getPendingDays() - request.getDaysRequested()));
                        leaveBalanceRepository.save(b);
                    });
        }

        return toResponse(leaveRequestRepository.save(request));
    }

    @Transactional
    public LeaveRequestResponse cancelLeave(Long requestId, Long employeeId) {
        LeaveRequest request = findRequest(requestId);
        if (!request.getEmployee().getId().equals(employeeId)) {
            throw new IllegalArgumentException("You can only cancel your own leave requests");
        }
        if (request.getStatus() == LeaveStatus.APPROVED || request.getStatus() == LeaveStatus.REJECTED) {
            throw new LeaveRequestStateException("Cannot cancel an already " + request.getStatus().name().toLowerCase() + " request");
        }
        if (request.getStatus() == LeaveStatus.CANCELLED) {
            throw new LeaveRequestStateException("Request is already cancelled");
        }

        // Release pending days
        if (request.getLeaveType() != LeaveType.UNPAID) {
            int year = request.getStartDate().getYear();
            leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeAndYear(
                            request.getEmployee().getId(), request.getLeaveType(), year)
                    .ifPresent(b -> {
                        b.setPendingDays(Math.max(0, b.getPendingDays() - request.getDaysRequested()));
                        leaveBalanceRepository.save(b);
                    });
        }

        request.setStatus(LeaveStatus.CANCELLED);
        return toResponse(leaveRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getAll(Pageable pageable) {
        Page<LeaveRequest> page = leaveRequestRepository.findAll(pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getByEmployee(Long employeeId, Pageable pageable) {
        findEmployee(employeeId);
        Page<LeaveRequest> page = leaveRequestRepository.findByEmployeeId(employeeId, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveRequestResponse> getByStatus(LeaveStatus status, Pageable pageable) {
        Page<LeaveRequest> page = leaveRequestRepository.findByStatus(status, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long id) {
        return toResponse(findRequest(id));
    }

    // ── Leave Balances ─────────────────────────────────────────────────────────

    @Transactional
    public LeaveBalanceResponse setBalance(LeaveBalanceRequest dto) {
        Employee employee = findEmployee(dto.getEmployeeId());

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeAndYear(dto.getEmployeeId(), dto.getLeaveType(), dto.getYear())
                .orElse(LeaveBalance.builder()
                        .employee(employee)
                        .leaveType(dto.getLeaveType())
                        .year(dto.getYear())
                        .build());

        balance.setTotalDays(dto.getTotalDays());
        return toBalanceResponse(leaveBalanceRepository.save(balance));
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getBalances(Long employeeId, int year) {
        findEmployee(employeeId);
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .stream().map(this::toBalanceResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int countWorkingDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    private Employee findEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private LeaveRequest findRequest(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + id));
    }

    private LeaveRequestResponse toResponse(LeaveRequest r) {
        LeaveRequestResponse resp = new LeaveRequestResponse();
        resp.setId(r.getId());
        resp.setEmployeeId(r.getEmployee().getId());
        resp.setEmployeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName());
        resp.setLeaveType(r.getLeaveType());
        resp.setStartDate(r.getStartDate());
        resp.setEndDate(r.getEndDate());
        resp.setDaysRequested(r.getDaysRequested());
        resp.setReason(r.getReason());
        resp.setStatus(r.getStatus());
        resp.setRejectionReason(r.getRejectionReason());
        resp.setReviewedAt(r.getReviewedAt());
        resp.setCreatedAt(r.getCreatedAt());
        if (r.getReviewedBy() != null) {
            resp.setReviewedById(r.getReviewedBy().getId());
            resp.setReviewedByName(r.getReviewedBy().getFirstName() + " " + r.getReviewedBy().getLastName());
        }
        return resp;
    }

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance b) {
        LeaveBalanceResponse resp = new LeaveBalanceResponse();
        resp.setId(b.getId());
        resp.setEmployeeId(b.getEmployee().getId());
        resp.setEmployeeName(b.getEmployee().getFirstName() + " " + b.getEmployee().getLastName());
        resp.setLeaveType(b.getLeaveType());
        resp.setYear(b.getYear());
        resp.setTotalDays(b.getTotalDays());
        resp.setUsedDays(b.getUsedDays());
        resp.setPendingDays(b.getPendingDays());
        resp.setRemainingDays(b.getRemainingDays());
        return resp;
    }

    private PagedResponse<LeaveRequestResponse> toPagedResponse(Page<LeaveRequest> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
