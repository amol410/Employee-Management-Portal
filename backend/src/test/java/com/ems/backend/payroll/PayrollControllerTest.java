package com.ems.backend.payroll;

import com.ems.backend.entity.*;
import com.ems.backend.repository.*;
import com.ems.backend.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayrollControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired PayrollRecordRepository payrollRecordRepository;
    @Autowired SalaryRevisionRepository salaryRevisionRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;
    private String adminToken;
    private String employeeToken;

    private static Long empId;
    private static Long mgr1Id;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac, builder ->
                builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());

        payrollRecordRepository.deleteAll();
        salaryRevisionRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createToken("admin_pay", Role.ROLE_ADMIN);
        employeeToken = createToken("emp_pay", Role.ROLE_EMPLOYEE);

        Employee emp = employeeRepository.save(Employee.builder()
                .firstName("Alice").lastName("Dev").email("alice@pay.com")
                .salary(BigDecimal.valueOf(80000)).build());
        empId = emp.getId();

        Employee mgr = employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Boss").email("bob@pay.com")
                .salary(BigDecimal.valueOf(120000)).build());
        mgr1Id = mgr.getId();
    }

    private String createToken(String username, Role role) {
        User user = userRepository.save(User.builder()
                .username(username).email(username + "@ems.com")
                .password(passwordEncoder.encode("pass")).role(role).build());
        org.springframework.security.core.userdetails.UserDetails ud =
                new org.springframework.security.core.userdetails.User(
                        user.getUsername(), user.getPassword(),
                        List.of(new SimpleGrantedAuthority(role.name())));
        return jwtUtil.generateToken(ud);
    }

    private Long generatePayroll(int month, int year) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "payMonth", month,
                        "payYear", year,
                        "allowances", 5000,
                        "deductions", 3000)))
                .exchange();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── Payroll Tests ──────────────────────────────────────────────────────────

    @Test @Order(1)
    void generatePayroll_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 3, "payYear", 2026,
                        "allowances", 5000, "deductions", 3000))))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("DRAFT");
    }

    @Test @Order(2)
    void generatePayroll_netPayCalculatedCorrectly() throws Exception {
        // basic=80000, allowances=5000, deductions=3000, net=82000
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 4, "payYear", 2026,
                        "allowances", 5000, "deductions", 3000))))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.netPay").isEqualTo(82000.0);
    }

    @Test @Order(3)
    void generatePayroll_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 5, "payYear", 2026))))
                .hasStatus(403);
    }

    @Test @Order(4)
    void generatePayroll_duplicate_returns409() throws Exception {
        generatePayroll(6, 2026);
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 6, "payYear", 2026))))
                .hasStatus(409);
    }

    @Test @Order(5)
    void processPayroll_draftToProcessed_returns200() throws Exception {
        Long id = generatePayroll(7, 2026);
        assertThat(mvc.patch().uri("/api/payroll/" + id + "/process")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.status").isEqualTo("PROCESSED");
    }

    @Test @Order(6)
    void processPayroll_alreadyProcessed_returns409() throws Exception {
        Long id = generatePayroll(8, 2026);
        mvc.patch().uri("/api/payroll/" + id + "/process")
                .header("Authorization", "Bearer " + adminToken).exchange();
        assertThat(mvc.patch().uri("/api/payroll/" + id + "/process")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(409);
    }

    @Test @Order(7)
    void markPaid_processedToPaid_returns200() throws Exception {
        Long id = generatePayroll(9, 2026);
        mvc.patch().uri("/api/payroll/" + id + "/process")
                .header("Authorization", "Bearer " + adminToken).exchange();
        assertThat(mvc.patch().uri("/api/payroll/" + id + "/pay")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.status").isEqualTo("PAID");
    }

    @Test @Order(8)
    void markPaid_fromDraft_returns409() throws Exception {
        Long id = generatePayroll(10, 2026);
        assertThat(mvc.patch().uri("/api/payroll/" + id + "/pay")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(409);
    }

    @Test @Order(9)
    void getPayrollById_returns200() throws Exception {
        Long id = generatePayroll(11, 2026);
        assertThat(mvc.get().uri("/api/payroll/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.id").isEqualTo(id.intValue());
    }

    @Test @Order(10)
    void getPayrollById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/payroll/9999")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(404);
    }

    @Test @Order(11)
    void getAllPayrolls_asAdmin_returns200() throws Exception {
        generatePayroll(1, 2026);
        assertThat(mvc.get().uri("/api/payroll")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200);
    }

    @Test @Order(12)
    void getAllPayrolls_asEmployee_returns403() throws Exception {
        assertThat(mvc.get().uri("/api/payroll")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(403);
    }

    @Test @Order(13)
    void getPayrollByEmployee_returns200() throws Exception {
        generatePayroll(2, 2026);
        assertThat(mvc.get().uri("/api/payroll/employee/" + empId)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isNotNull();
    }

    @Test @Order(14)
    void filterPayrollByMonthYear_returns200() throws Exception {
        generatePayroll(3, 2025);
        assertThat(mvc.get().uri("/api/payroll")
                .param("month", "3").param("year", "2025")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200);
    }

    // ── Salary Revision Tests ──────────────────────────────────────────────────

    @Test @Order(15)
    void reviseSalary_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/payroll/salary-revisions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "newSalary", 90000,
                        "effectiveDate", "2026-04-01",
                        "reason", "Annual increment",
                        "revisedById", mgr1Id))))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.newSalary").isEqualTo(90000);
    }

    @Test @Order(16)
    void reviseSalary_updatesEmployeeSalary() throws Exception {
        mvc.post().uri("/api/payroll/salary-revisions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "newSalary", 95000,
                        "effectiveDate", LocalDate.now().toString())))
                .exchange();

        // Verify employee salary updated — generate payroll and check basicSalary
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 12, "payYear", 2025))))
                .hasStatus(201)
                .bodyJson().extractingPath("$.basicSalary").isEqualTo(95000.0);
    }

    @Test @Order(17)
    void reviseSalary_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/payroll/salary-revisions")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "newSalary", 100000,
                        "effectiveDate", "2026-04-01"))))
                .hasStatus(403);
    }

    @Test @Order(18)
    void getSalaryRevisionsByEmployee_returns200() throws Exception {
        mvc.post().uri("/api/payroll/salary-revisions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "newSalary", 85000,
                        "effectiveDate", LocalDate.now().toString())))
                .exchange();

        assertThat(mvc.get().uri("/api/payroll/salary-revisions/employee/" + empId)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isNotNull();
    }

    @Test @Order(19)
    void getAllSalaryRevisions_asAdmin_returns200() throws Exception {
        assertThat(mvc.get().uri("/api/payroll/salary-revisions")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200);
    }

    @Test @Order(20)
    void generatePayroll_invalidMonth_returns400() throws Exception {
        assertThat(mvc.post().uri("/api/payroll/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "payMonth", 13, "payYear", 2026))))
                .hasStatus(400);
    }
}
