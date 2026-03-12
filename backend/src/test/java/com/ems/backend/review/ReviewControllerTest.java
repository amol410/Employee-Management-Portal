package com.ems.backend.review;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReviewControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired PerformanceReviewRepository reviewRepository;
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
    private static Long reviewId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac, builder ->
                builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());

        reviewRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createToken("admin_rev", Role.ROLE_ADMIN);
        employeeToken = createToken("emp_rev", Role.ROLE_EMPLOYEE);

        Employee emp = employeeRepository.save(Employee.builder()
                .firstName("Rev").lastName("Employee").email("rev_emp@test.com")
                .salary(BigDecimal.valueOf(60000)).build());
        empId = emp.getId();

        Employee mgr = employeeRepository.save(Employee.builder()
                .firstName("Rev").lastName("Manager").email("rev_mgr@test.com")
                .salary(BigDecimal.valueOf(100000)).build());
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

    private Long createReview(int rating, String period) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", period,
                        "rating", rating,
                        "comments", "Test comment",
                        "goals", "Test goal")))
                .exchange();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── Review Tests ───────────────────────────────────────────────────────────

    @Test @Order(1)
    void createReview_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q1",
                        "rating", 4,
                        "comments", "Good work",
                        "goals", "Improve testing coverage"))))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("DRAFT");
    }

    @Test @Order(2)
    void createReview_ratingIsStored() throws Exception {
        assertThat(mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q2",
                        "rating", 5))))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.rating").isEqualTo(5);
    }

    @Test @Order(3)
    void createReview_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q3",
                        "rating", 3))))
                .hasStatus(403);
    }

    @Test @Order(4)
    void createReview_noToken_returns401() throws Exception {
        assertThat(mvc.post().uri("/api/reviews")
                .contentType("application/json")
                .content("{}"))
                .hasStatus(401);
    }

    @Test @Order(5)
    void createReview_duplicate_returns409() throws Exception {
        createReview(4, "2026-Q1");
        assertThat(mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q1",
                        "rating", 3))))
                .hasStatus(409);
    }

    @Test @Order(6)
    void updateReview_draft_returns200() throws Exception {
        Long id = createReview(3, "2026-Q1");
        assertThat(mvc.put().uri("/api/reviews/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q1",
                        "rating", 5,
                        "comments", "Excellent"))))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.rating").isEqualTo(5);
    }

    @Test @Order(7)
    void submitReview_draftToSubmitted_returns200() throws Exception {
        Long id = createReview(4, "2026-Q1");
        assertThat(mvc.patch().uri("/api/reviews/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("SUBMITTED");
    }

    @Test @Order(8)
    void updateReview_afterSubmit_returns409() throws Exception {
        Long id = createReview(4, "2026-Q1");
        mvc.patch().uri("/api/reviews/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken).exchange();

        assertThat(mvc.put().uri("/api/reviews/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q1",
                        "rating", 2))))
                .hasStatus(409);
    }

    @Test @Order(9)
    void acknowledgeReview_submittedToAcknowledged_returns200() throws Exception {
        Long id = createReview(4, "2026-Q1");
        mvc.patch().uri("/api/reviews/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken).exchange();

        assertThat(mvc.patch().uri("/api/reviews/" + id + "/acknowledge")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("ACKNOWLEDGED");
    }

    @Test @Order(10)
    void acknowledgeReview_fromDraft_returns409() throws Exception {
        Long id = createReview(4, "2026-Q1");
        assertThat(mvc.patch().uri("/api/reviews/" + id + "/acknowledge")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(409);
    }

    @Test @Order(11)
    void getReviewById_returns200() throws Exception {
        Long id = createReview(3, "2026-Q1");
        assertThat(mvc.get().uri("/api/reviews/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.id").isEqualTo(id.intValue());
    }

    @Test @Order(12)
    void getReviewById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/reviews/99999")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(404);
    }

    @Test @Order(13)
    void getAllReviews_asAdmin_returns200() throws Exception {
        createReview(4, "2026-Q1");
        assertThat(mvc.get().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.content").isNotNull();
    }

    @Test @Order(14)
    void getAllReviews_asEmployee_returns403() throws Exception {
        assertThat(mvc.get().uri("/api/reviews")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(403);
    }

    @Test @Order(15)
    void getReviewsByEmployee_returns200() throws Exception {
        createReview(4, "2026-Q1");
        assertThat(mvc.get().uri("/api/reviews/employee/" + empId)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.content").isNotNull();
    }

    @Test @Order(16)
    void getReviewsByReviewer_returns200() throws Exception {
        createReview(5, "2026-Q1");
        assertThat(mvc.get().uri("/api/reviews/reviewer/" + mgr1Id)
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.totalElements").isNotNull();
    }

    // ── Analytics Tests ────────────────────────────────────────────────────────

    @Test @Order(17)
    void getAnalyticsSummary_asAdmin_returns200() throws Exception {
        assertThat(mvc.get().uri("/api/analytics/summary")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.totalEmployees").isNotNull();
    }

    @Test @Order(18)
    void getAnalyticsSummary_totalReviewsReflectsData() throws Exception {
        createReview(4, "2026-Q1");
        assertThat(mvc.get().uri("/api/analytics/summary")
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.totalReviews").isEqualTo(1);
    }

    @Test @Order(19)
    void getAnalyticsSummary_asEmployee_returns403() throws Exception {
        assertThat(mvc.get().uri("/api/analytics/summary")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(403);
    }

    @Test @Order(20)
    void createReview_invalidRating_returns400() throws Exception {
        assertThat(mvc.post().uri("/api/reviews")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "employeeId", empId,
                        "reviewerId", mgr1Id,
                        "reviewPeriod", "2026-Q1",
                        "rating", 10))))
                .hasStatus(400);
    }
}
