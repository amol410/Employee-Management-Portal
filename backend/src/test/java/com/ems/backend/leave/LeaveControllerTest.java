package com.ems.backend.leave;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaveControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired LeaveBalanceRepository leaveBalanceRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;
    private String adminToken;
    private String employeeToken;

    private static Long empId;
    private static Long managerId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac, builder ->
                builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());

        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createToken("admin_leave", Role.ROLE_ADMIN);
        employeeToken = createToken("emp_leave", Role.ROLE_EMPLOYEE);

        Employee emp = employeeRepository.save(Employee.builder()
                .firstName("Jane").lastName("Smith").email("jane@leave.com")
                .salary(BigDecimal.valueOf(60000)).build());
        empId = emp.getId();

        Employee mgr = employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Boss").email("bob@leave.com")
                .salary(BigDecimal.valueOf(90000)).build());
        managerId = mgr.getId();
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

    private void setupBalance(Long eId, LeaveType type, int total) throws Exception {
        mvc.post().uri("/api/leaves/balances")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", eId, "leaveType", type.name(),
                        "year", LocalDate.now().getYear(), "totalDays", total)))
                .exchange();
    }

    private Long applyLeaveAndGetId(LocalDate start, LocalDate end) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/leaves/apply")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "startDate", start.toString(), "endDate", end.toString())))
                .exchange();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test @Order(1)
    void setLeaveBalance_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/leaves/balances")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "year", LocalDate.now().getYear(), "totalDays", 20))))
                .hasStatus(201)
                .bodyJson().extractingPath("$.totalDays").isEqualTo(20);
    }

    @Test @Order(2)
    void setLeaveBalance_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/leaves/balances")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "year", LocalDate.now().getYear(), "totalDays", 20))))
                .hasStatus(403);
    }

    @Test @Order(3)
    void applyLeave_success_returns201() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        LocalDate monday = nextMonday();
        LocalDate friday = monday.plusDays(4);

        assertThat(mvc.post().uri("/api/leaves/apply")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "startDate", monday.toString(), "endDate", friday.toString(),
                        "reason", "Vacation"))))
                .hasStatus(201)
                .bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    }

    @Test @Order(4)
    void applyLeave_insufficientBalance_returns422() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 2);
        LocalDate monday = nextMonday();
        LocalDate friday = monday.plusDays(4);

        assertThat(mvc.post().uri("/api/leaves/apply")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "startDate", monday.toString(), "endDate", friday.toString()))))
                .hasStatus(422);
    }

    @Test @Order(5)
    void applyLeave_endBeforeStart_returns400() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);

        assertThat(mvc.post().uri("/api/leaves/apply")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "ANNUAL",
                        "startDate", "2026-06-10", "endDate", "2026-06-05"))))
                .hasStatus(400);
    }

    @Test @Order(6)
    void approveLeave_asAdmin_returns200() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday().plusDays(4));

        assertThat(mvc.patch().uri("/api/leaves/" + id + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("reviewedById", managerId))))
                .hasStatus(200)
                .bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
    }

    @Test @Order(7)
    void approveLeave_asEmployee_returns403() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday().plusDays(4));

        assertThat(mvc.patch().uri("/api/leaves/" + id + "/approve")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("reviewedById", managerId))))
                .hasStatus(403);
    }

    @Test @Order(8)
    void rejectLeave_returns200() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday().plusDays(4));

        assertThat(mvc.patch().uri("/api/leaves/" + id + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "reviewedById", managerId,
                        "rejectionReason", "Not approved at this time"))))
                .hasStatus(200)
                .bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
    }

    @Test @Order(9)
    void cancelLeave_byEmployee_returns200() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday().plusDays(4));

        assertThat(mvc.patch().uri("/api/leaves/" + id + "/cancel")
                .header("Authorization", "Bearer " + employeeToken)
                .param("employeeId", empId.toString()))
                .hasStatus(200)
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    }

    @Test @Order(10)
    void approveAlreadyApproved_returns409() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday().plusDays(4));

        // First approval
        mvc.patch().uri("/api/leaves/" + id + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("reviewedById", managerId)))
                .exchange();

        // Second approval → 409
        assertThat(mvc.patch().uri("/api/leaves/" + id + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("reviewedById", managerId))))
                .hasStatus(409);
    }

    @Test @Order(11)
    void getLeavesByEmployee_returns200() throws Exception {
        assertThat(mvc.get().uri("/api/leaves/employee/" + empId)
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isNotNull();
    }

    @Test @Order(12)
    void getAllLeaves_asAdmin_returns200() throws Exception {
        assertThat(mvc.get().uri("/api/leaves")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200);
    }

    @Test @Order(13)
    void getAllLeaves_asEmployee_returns403() throws Exception {
        assertThat(mvc.get().uri("/api/leaves")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(403);
    }

    @Test @Order(14)
    void getLeaveBalances_returns200() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        assertThat(mvc.get().uri("/api/leaves/balances/" + empId)
                .param("year", String.valueOf(LocalDate.now().getYear()))
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200);
    }

    @Test @Order(15)
    void applyUnpaidLeave_noBalanceNeeded_returns201() throws Exception {
        LocalDate monday = nextMonday();

        assertThat(mvc.post().uri("/api/leaves/apply")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId, "leaveType", "UNPAID",
                        "startDate", monday.toString(), "endDate", monday.toString()))))
                .hasStatus(201);
    }

    @Test @Order(16)
    void getLeaveById_returns200() throws Exception {
        setupBalance(empId, LeaveType.ANNUAL, 20);
        Long id = applyLeaveAndGetId(nextMonday(), nextMonday());

        assertThat(mvc.get().uri("/api/leaves/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.id").isEqualTo(id.intValue());
    }

    @Test @Order(17)
    void getLeaveById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/leaves/9999")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(404);
    }
}
