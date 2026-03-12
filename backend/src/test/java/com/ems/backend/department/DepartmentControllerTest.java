package com.ems.backend.department;

import com.ems.backend.entity.Department;
import com.ems.backend.entity.Employee;
import com.ems.backend.entity.Role;
import com.ems.backend.entity.User;
import com.ems.backend.repository.DepartmentRepository;
import com.ems.backend.repository.EmployeeRepository;
import com.ems.backend.repository.UserRepository;
import com.ems.backend.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DepartmentControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;
    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac, builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createTokenForRole("admin_dept", Role.ROLE_ADMIN);
        employeeToken = createTokenForRole("emp_dept", Role.ROLE_EMPLOYEE);
    }

    private String createTokenForRole(String username, Role role) {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@ems.com")
                .password(passwordEncoder.encode("pass"))
                .role(role)
                .build());
        org.springframework.security.core.userdetails.UserDetails ud =
                new org.springframework.security.core.userdetails.User(
                        user.getUsername(), user.getPassword(),
                        List.of(new SimpleGrantedAuthority(role.name())));
        return jwtUtil.generateToken(ud);
    }

    private Department savedDept(String name) {
        return departmentRepository.save(Department.builder()
                .name(name).description("Test dept").build());
    }

    @Test @Order(1)
    void createDepartment_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/departments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "Engineering", "description", "Dev team"))))
                .hasStatus(201)
                .bodyJson().extractingPath("$.name").isEqualTo("Engineering");
    }

    @Test @Order(2)
    void createDepartment_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/departments")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "HR"))))
                .hasStatus(403);
    }

    @Test @Order(3)
    void createDepartment_noToken_returns401() throws Exception {
        assertThat(mvc.post().uri("/api/departments")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "Finance"))))
                .hasStatus(401);
    }

    @Test @Order(4)
    void createDepartment_duplicateName_returns409() throws Exception {
        savedDept("Engineering");
        assertThat(mvc.post().uri("/api/departments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "Engineering"))))
                .hasStatus(409);
    }

    @Test @Order(5)
    void getAllDepartments_returns200() throws Exception {
        savedDept("Engineering");
        savedDept("HR");
        assertThat(mvc.get().uri("/api/departments")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.length()").isEqualTo(2);
    }

    @Test @Order(6)
    void getDepartmentById_found_returns200() throws Exception {
        Department dept = savedDept("Finance");
        assertThat(mvc.get().uri("/api/departments/" + dept.getId())
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.name").isEqualTo("Finance");
    }

    @Test @Order(7)
    void getDepartmentById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/departments/9999")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(404);
    }

    @Test @Order(8)
    void updateDepartment_asAdmin_returns200() throws Exception {
        Department dept = savedDept("OldName");
        assertThat(mvc.put().uri("/api/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "NewName", "description", "Updated"))))
                .hasStatus(200)
                .bodyJson().extractingPath("$.name").isEqualTo("NewName");
    }

    @Test @Order(9)
    void deleteDepartment_empty_returns204() throws Exception {
        Department dept = savedDept("ToDelete");
        assertThat(mvc.delete().uri("/api/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(204);
    }

    @Test @Order(10)
    void deleteDepartment_withEmployees_returns409() throws Exception {
        Department dept = savedDept("Occupied");
        employeeRepository.save(Employee.builder()
                .firstName("John").lastName("Doe").email("jd@test.com")
                .salary(BigDecimal.valueOf(50000)).department(dept).build());

        assertThat(mvc.delete().uri("/api/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(409);
    }

    @Test @Order(11)
    void getDepartment_employeeCount_correct() throws Exception {
        Department dept = savedDept("Counted");
        employeeRepository.save(Employee.builder()
                .firstName("A").lastName("B").email("ab@test.com")
                .salary(BigDecimal.valueOf(50000)).department(dept).build());
        employeeRepository.save(Employee.builder()
                .firstName("C").lastName("D").email("cd@test.com")
                .salary(BigDecimal.valueOf(50000)).department(dept).build());

        assertThat(mvc.get().uri("/api/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.employeeCount").isEqualTo(2);
    }

    @Test @Order(12)
    void filterEmployeesByDepartment_returns200() throws Exception {
        Department eng = savedDept("Engineering2");
        Department hr = savedDept("HR2");
        employeeRepository.save(Employee.builder()
                .firstName("Eng").lastName("User").email("eng@test.com")
                .salary(BigDecimal.valueOf(70000)).department(eng).build());
        employeeRepository.save(Employee.builder()
                .firstName("HR").lastName("User").email("hr@test.com")
                .salary(BigDecimal.valueOf(60000)).department(hr).build());

        assertThat(mvc.get().uri("/api/employees").param("departmentId", eng.getId().toString())
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }
}
