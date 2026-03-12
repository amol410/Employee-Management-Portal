package com.ems.backend.controller;

import com.ems.backend.entity.Employee;
import com.ems.backend.repository.EmployeeRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac);
        employeeRepository.deleteAll();
    }

    private Map<String, Object> validPayload() {
        return Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "email", "jane@example.com",
                "phone", "9876543210",
                "hireDate", "2024-01-15",
                "position", "Software Engineer",
                "salary", 60000
        );
    }

    @Test
    @Order(1)
    void createEmployee_success_returns201() throws Exception {
        assertThat(mvc.post().uri("/api/employees")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload())))
                .hasStatus(201)
                .bodyJson().extractingPath("$.email").isEqualTo("jane@example.com");
    }

    @Test
    @Order(2)
    void createEmployee_duplicateEmail_returns409() throws Exception {
        mvc.post().uri("/api/employees")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload()))
                .exchange();

        assertThat(mvc.post().uri("/api/employees")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validPayload())))
                .hasStatus(409);
    }

    @Test
    @Order(3)
    void createEmployee_missingEmail_returns400() throws Exception {
        Map<String, Object> bad = Map.of("firstName", "No", "lastName", "Email");
        assertThat(mvc.post().uri("/api/employees")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(bad)))
                .hasStatus(400);
    }

    @Test
    @Order(4)
    void getAllEmployees_returnsList() throws Exception {
        employeeRepository.save(Employee.builder()
                .firstName("Alice").lastName("Brown").email("alice@example.com")
                .salary(BigDecimal.valueOf(55000)).build());

        assertThat(mvc.get().uri("/api/employees"))
                .hasStatus(200)
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @Order(5)
    void getEmployeeById_notFound_returns404() throws Exception {
        assertThat(mvc.get().uri("/api/employees/9999"))
                .hasStatus(404);
    }

    @Test
    @Order(6)
    void updateEmployee_success_returns200() throws Exception {
        Employee saved = employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Jones").email("bob@example.com")
                .salary(BigDecimal.valueOf(40000)).build());

        Map<String, Object> update = Map.of(
                "firstName", "Bobby",
                "lastName", "Jones",
                "email", "bob@example.com",
                "salary", 45000
        );

        assertThat(mvc.put().uri("/api/employees/" + saved.getId())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(update)))
                .hasStatus(200)
                .bodyJson().extractingPath("$.firstName").isEqualTo("Bobby");
    }

    @Test
    @Order(7)
    void deleteEmployee_success_returns204() throws Exception {
        Employee saved = employeeRepository.save(Employee.builder()
                .firstName("Del").lastName("Me").email("del@example.com")
                .salary(BigDecimal.valueOf(30000)).build());

        assertThat(mvc.delete().uri("/api/employees/" + saved.getId()))
                .hasStatus(204);

        assertThat(mvc.get().uri("/api/employees/" + saved.getId()))
                .hasStatus(404);
    }

    @Test
    @Order(8)
    void searchEmployees_byName_returnsMatch() throws Exception {
        employeeRepository.save(Employee.builder()
                .firstName("SearchMe").lastName("Test").email("searchme@example.com")
                .salary(BigDecimal.valueOf(50000)).build());
        employeeRepository.save(Employee.builder()
                .firstName("Other").lastName("Person").email("other@example.com")
                .salary(BigDecimal.valueOf(50000)).build());

        assertThat(mvc.get().uri("/api/employees").param("search", "SearchMe"))
                .hasStatus(200)
                .bodyJson().extractingPath("$.content[0].firstName").isEqualTo("SearchMe");
    }
}
