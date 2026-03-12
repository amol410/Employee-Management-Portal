package com.ems.backend.dto;

import com.ems.backend.entity.LeaveType;
import lombok.Data;

@Data
public class LeaveBalanceResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LeaveType leaveType;
    private Integer year;
    private Integer totalDays;
    private Integer usedDays;
    private Integer pendingDays;
    private Integer remainingDays;
}
