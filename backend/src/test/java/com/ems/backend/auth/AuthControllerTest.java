package com.ems.backend.auth;

import com.ems.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(wac, builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());
        userRepository.deleteAll();
    }

    private Map<String, String> validRegisterPayload() {
        return Map.of(
                "username", "testuser",
                "email", "testuser@example.com",
                "password", "secret123"
        );
    }

    @Test
    @Order(1)
    void register_success_returns201WithToken() throws Exception {
        assertThat(mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload())))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.token").asString().isNotBlank();
    }

    @Test
    @Order(2)
    void register_duplicateUsername_returns409() throws Exception {
        mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload()))
                .exchange();

        assertThat(mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload())))
                .hasStatus(409);
    }

    @Test
    @Order(3)
    void register_duplicateEmail_returns409() throws Exception {
        mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload()))
                .exchange();

        Map<String, String> sameEmail = Map.of(
                "username", "otheruser",
                "email", "testuser@example.com",
                "password", "secret123"
        );

        assertThat(mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(sameEmail)))
                .hasStatus(409);
    }

    @Test
    @Order(4)
    void register_invalidPayload_returns400() throws Exception {
        Map<String, String> bad = Map.of("username", "ab", "email", "not-email", "password", "123");
        assertThat(mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(bad)))
                .hasStatus(400);
    }

    @Test
    @Order(5)
    void login_success_returns200WithToken() throws Exception {
        mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload()))
                .exchange();

        Map<String, String> loginPayload = Map.of(
                "username", "testuser",
                "password", "secret123"
        );

        assertThat(mvc.post().uri("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(loginPayload)))
                .hasStatus(200)
                .bodyJson()
                .extractingPath("$.token").asString().isNotBlank();
    }

    @Test
    @Order(6)
    void login_wrongPassword_returns401() throws Exception {
        mvc.post().uri("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRegisterPayload()))
                .exchange();

        Map<String, String> badLogin = Map.of(
                "username", "testuser",
                "password", "wrongpassword"
        );

        assertThat(mvc.post().uri("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(badLogin)))
                .hasStatus(401);
    }

    @Test
    @Order(7)
    void login_nonExistentUser_returns401() throws Exception {
        Map<String, String> badLogin = Map.of(
                "username", "nobody",
                "password", "doesntmatter"
        );

        assertThat(mvc.post().uri("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(badLogin)))
                .hasStatus(401);
    }
}
