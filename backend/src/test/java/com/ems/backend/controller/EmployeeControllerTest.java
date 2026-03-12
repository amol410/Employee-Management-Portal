package com.ems.backend.controller;

import com.ems.backend.entity.Employee;
import com.ems.backend.repository.EmployeeRepository;
import com.ems.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmployeeControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;
    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcTester.from(wac, builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        adminToken = registerAndGetToken("admin", "admin@ems.com", "admin123", true);
        employeeToken = registerAndGetToken("empuser", "empuser@ems.com", "emp123", false);
    }

    private String registerAndGetToken(String username, String email, String password, boolean makeAdmin) throws Exception {
        Map<String, String> payload = Map.of("username", username, "email", email, "password", password);

        var result = mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payload))
                .exchange();

        String body = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();

        // Promote to ADMIN in DB if needed
        if (makeAdmin) {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setRole(com.ems.backend.entity.Role.ROLE_ADMIN);
                userRepository.save(user);
            });
            // Re-login to get token with updated role
            Map<String, String> login = Map.of("username", username, "password", password);
            var loginResult = mvc.post().uri("/api/auth/login")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(login))
                    .exchange();
            String loginBody = loginResult.getResponse().getContentAsString();
            token = objectMapper.readTree(loginBody).get("token").asText();
        }
        return token;
    }

    private Map<String, Object> validPayload(String email) {
        return Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "email", email,
                "phone", "9876543210",
                "hireDate", "2024-01-15",
                "position", "Software Engineer",
                "salary", 60000
        );
    }

    @Test
    @Order(1)
    void createEmployee_asAdmin_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/employees")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload("jane@example.com"))))
                .hasStatus(201)
                .bodyJson().extractingPath("$.email").isEqualTo("jane@example.com");
    }

    @Test
    @Order(2)
    void createEmployee_asEmployee_returns403() throws Exception {
        assertThat(mvc.post().uri("/api/employees")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload("jane2@example.com"))))
                .hasStatus(403);
    }

    @Test
    @Order(3)
    void createEmployee_noToken_returns401() throws Exception {
        assertThat(mvc.post().uri("/api/employees")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload("jane3@example.com"))))
                .hasStatus(401);
    }

    @Test
    @Order(4)
    void createEmployee_duplicateEmail_returns409() throws Exception {
        mvc.post().uri("/api/employees")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload("dup@example.com")))
                .exchange();

        assertThat(mvc.post().uri("/api/employees")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload("dup@example.com"))))
                .hasStatus(409);
    }

    @Test
    @Order(5)
    void getAllEmployees_authenticated_returns200() throws Exception {
        employeeRepository.save(Employee.builder()
                .firstName("Alice").lastName("Brown").email("alice@example.com")
                .salary(BigDecimal.valueOf(55000)).build());

        assertThat(mvc.get().uri("/api/employees")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @Order(6)
    void getAllEmployees_noToken_returns401() throws Exception {
        assertThat(mvc.get().uri("/api/employees"))
                .hasStatus(401);
    }

    @Test
    @Order(7)
    void getEmployeeById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/employees/9999")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(404);
    }

    @Test
    @Order(8)
    void updateEmployee_asAdmin_returns200() throws Exception {
        Employee saved = employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Jones").email("bob@example.com")
                .salary(BigDecimal.valueOf(40000)).build());

        Map<String, Object> update = Map.of(
                "firstName", "Bobby", "lastName", "Jones",
                "email", "bob@example.com", "salary", 45000
        );

        assertThat(mvc.put().uri("/api/employees/" + saved.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(update)))
                .hasStatus(200)
                .bodyJson().extractingPath("$.firstName").isEqualTo("Bobby");
    }

    @Test
    @Order(9)
    void deleteEmployee_asAdmin_returns204() throws Exception {
        Employee saved = employeeRepository.save(Employee.builder()
                .firstName("Del").lastName("Me").email("del@example.com")
                .salary(BigDecimal.valueOf(30000)).build());

        assertThat(mvc.delete().uri("/api/employees/" + saved.getId())
                .header("Authorization", "Bearer " + adminToken))
                .hasStatus(204);

        assertThat(mvc.get().uri("/api/employees/" + saved.getId())
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(404);
    }

    @Test
    @Order(10)
    void deleteEmployee_asEmployee_returns403() throws Exception {
        Employee saved = employeeRepository.save(Employee.builder()
                .firstName("Keep").lastName("Me").email("keep@example.com")
                .salary(BigDecimal.valueOf(30000)).build());

        assertThat(mvc.delete().uri("/api/employees/" + saved.getId())
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(403);
    }

    @Test
    @Order(11)
    void searchEmployees_byName_returnsMatch() throws Exception {
        employeeRepository.save(Employee.builder()
                .firstName("SearchMe").lastName("Test").email("searchme@example.com")
                .salary(BigDecimal.valueOf(50000)).build());
        employeeRepository.save(Employee.builder()
                .firstName("Other").lastName("Person").email("other@example.com")
                .salary(BigDecimal.valueOf(50000)).build());

        assertThat(mvc.get().uri("/api/employees").param("search", "SearchMe")
                .header("Authorization", "Bearer " + employeeToken))
                .hasStatus(200)
                .bodyJson().extractingPath("$.content[0].firstName").isEqualTo("SearchMe");
    }
}
