package com.ems.backend.mapper;

import com.ems.backend.dto.EmployeeRequest;
import com.ems.backend.dto.EmployeeResponse;
import com.ems.backend.entity.Employee;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    Employee toEntity(EmployeeRequest request);

    EmployeeResponse toResponse(Employee employee);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(EmployeeRequest request, @MappingTarget Employee employee);
}
