package com.ems.backend.dto;

import com.ems.backend.entity.LeaveStatus;
import com.ems.backend.entity.LeaveType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LeaveRequestResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer daysRequested;
    private String reason;
    private LeaveStatus status;
    private String rejectionReason;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
