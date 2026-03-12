package com.ems.backend.mapper;

import com.ems.backend.dto.EmployeeRequest;
import com.ems.backend.dto.EmployeeResponse;
import com.ems.backend.entity.Employee;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "department", ignore = true)
    @Mapping(target = "manager", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Employee toEntity(EmployeeRequest request);

    @Mapping(source = "department.id", target = "departmentId")
    @Mapping(source = "department.name", target = "departmentName")
    @Mapping(source = "manager.id", target = "managerId")
    @Mapping(expression = "java(employee.getManager() != null ? employee.getManager().getFirstName() + ' ' + employee.getManager().getLastName() : null)", target = "managerName")
    EmployeeResponse toResponse(Employee employee);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "manager", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(EmployeeRequest request, @MappingTarget Employee employee);
}
