# Employee Management System — Issues & Solutions (Version by Version)

---

## Version 1 — Project Setup & Basic Employee CRUD

### Issue 1: Spring Boot 4.x Jakarta EE Namespace Migration
**Problem:** All `javax.*` imports fail to compile. Spring Boot 4 fully migrated to Jakarta EE 10.
**Solution:** Replace all `javax.*` imports with `jakarta.*`:
- `javax.persistence.*` → `jakarta.persistence.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.servlet.*` → `jakarta.servlet.*`

### Issue 2: Lombok + Java 21 Annotation Processing
**Problem:** Lombok annotations (`@Data`, `@Builder`) cause compilation errors with newer Java versions.
**Solution:** Use Lombok `1.18.42+` in `pom.xml`. Ensure annotation processing is enabled in IDE and Maven Compiler Plugin is configured:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

### Issue 3: PostgreSQL Connection Refused on Startup
**Problem:** Application fails to start — `Connection refused` to PostgreSQL.
**Solution:** Ensure PostgreSQL is running (`pg_ctl start`). Verify `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ems_db
spring.datasource.username=postgres
spring.datasource.password=yourpassword
```
Create the database manually: `CREATE DATABASE ems_db;`

### Issue 4: H2 Test Database Dialect Conflict
**Problem:** H2 in-memory DB used for tests throws `GenerationTarget encountered exception` due to PostgreSQL-specific SQL.
**Solution:** Create `src/test/resources/application.properties` with H2 config and disable PostgreSQL-specific DDL:
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

### Issue 5: `spring.jpa.open-in-view` Deprecation Warning
**Problem:** Console shows warning about `spring.jpa.open-in-view` being enabled by default.
**Solution:** Explicitly disable it in `application.properties`:
```properties
spring.jpa.open-in-view=false
```

---

## Version 2 — Authentication & JWT Security

### Issue 1: `WebSecurityConfigurerAdapter` Removed in Spring Boot 4
**Problem:** `extends WebSecurityConfigurerAdapter` no longer compiles — class was removed.
**Solution:** Use component-based security configuration with `SecurityFilterChain` bean:
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

### Issue 2: JWT Library Compatibility
**Problem:** Old `io.jsonwebtoken:jjwt` `0.9.x` API breaks — `Jwts.parser()` chaining changed.
**Solution:** Upgrade to `jjwt` `0.12.x`:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```
Use new API: `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`

### Issue 3: `PasswordEncoder` Bean Circular Dependency
**Problem:** `AuthService` depends on `PasswordEncoder` which is in `SecurityConfig` which depends on `AuthService`.
**Solution:** Extract `PasswordEncoder` bean to a separate `@Configuration` class (`PasswordConfig.java`) with no other dependencies.

### Issue 4: JWT Token Not Passed Through FilterChain
**Problem:** All requests return 401 even with a valid token.
**Solution:** Ensure `JwtAuthFilter` is registered before `UsernamePasswordAuthenticationFilter`:
```java
http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```
Also ensure filter sets `SecurityContextHolder` correctly.

### Issue 5: CORS Blocking React Frontend Requests
**Problem:** Browser blocks requests from `localhost:3000` to `localhost:8080`.
**Solution:** Add a global CORS config bean:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    // register for all paths
}
```

---

## Version 3 — Department & Role Management

### Issue 1: Bidirectional JPA Relationship Causing `StackOverflowError`
**Problem:** `Employee` has `@ManyToOne Department` and `Department` has `@OneToMany List<Employee>` — JSON serialization causes infinite recursion.
**Solution:** Use `@JsonManagedReference` / `@JsonBackReference` or use DTOs to avoid serializing both sides of the relationship directly.

### Issue 2: Cascade Delete Constraint Violation
**Problem:** Deleting a department with assigned employees throws `DataIntegrityViolationException`.
**Solution:** Either set employees' department to null before deleting, or configure `CascadeType` carefully. Prefer a soft-delete approach or throw a business exception if employees exist:
```java
if (departmentRepository.hasEmployees(deptId)) {
    throw new BusinessException("Cannot delete department with active employees");
}
```

### Issue 3: `@Enumerated` Type Mismatch for Role Enum
**Problem:** Role stored as integer ordinal in DB — breaks when enum order changes.
**Solution:** Always use `@Enumerated(EnumType.STRING)` to store the enum name as a string.

### Issue 4: DTOs Not Mapping Nested Objects
**Problem:** `EmployeeDTO` doesn't include `departmentName` — only `departmentId`.
**Solution:** Use MapStruct with a custom mapping or add a `@Named` mapper method to resolve nested fields:
```java
@Mapping(source = "department.name", target = "departmentName")
EmployeeDTO toDto(Employee employee);
```

---

## Version 4 — Leave Management

### Issue 1: Date Overlap Validation Not Working
**Problem:** Employees could submit overlapping leave requests.
**Solution:** Add a JPQL query to check for date overlaps before saving:
```java
@Query("SELECT COUNT(l) > 0 FROM Leave l WHERE l.employee.id = :empId " +
       "AND l.startDate <= :end AND l.endDate >= :start AND l.status != 'REJECTED'")
boolean existsOverlappingLeave(Long empId, LocalDate start, LocalDate end);
```

### Issue 2: `LocalDate` Serialization Format Mismatch
**Problem:** Frontend sends dates as `"2025-01-15"` but Jackson deserializes incorrectly or throws errors.
**Solution:** Add `jackson-datatype-jsr310` dependency and configure:
```properties
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.date-format=yyyy-MM-dd
```

### Issue 3: Leave Balance Calculation Race Condition
**Problem:** Concurrent leave submissions could over-consume leave balance.
**Solution:** Use optimistic locking on the `LeaveBalance` entity:
```java
@Version
private Long version;
```
Catch `OptimisticLockingFailureException` and return a meaningful error.

### Issue 4: Email Notifications Not Sent in Test Environment
**Problem:** Integration tests fail because `JavaMailSender` bean is missing.
**Solution:** Add a conditional bean or mock the mail service in the test profile using `@MockBean` / `@Profile("test")`.

---

## Version 5 — Payroll & Salary Management

### Issue 1: `BigDecimal` Precision Loss in Salary Calculations
**Problem:** Floating-point arithmetic causes rounding errors in payslip calculations.
**Solution:** Always use `BigDecimal` with explicit `RoundingMode`:
```java
salary.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
```

### Issue 2: Payroll Re-generation Idempotency
**Problem:** Running payroll twice for the same month creates duplicate payslips.
**Solution:** Add a unique constraint on `(employee_id, month, year)` in the `payslip` table and catch `DataIntegrityViolationException` to return a clear error.

### Issue 3: Scheduled Payroll Job Running in Tests
**Problem:** `@Scheduled` payroll jobs fire during integration tests causing unexpected DB state.
**Solution:** Disable scheduling in test profile:
```properties
spring.task.scheduling.enabled=false
```
Or use `@ConditionalOnProperty` on the scheduler class.

### Issue 4: N+1 Query Problem on Payslip List
**Problem:** Loading all payslips for all employees triggers one query per employee.
**Solution:** Use `JOIN FETCH` in JPQL or use `@EntityGraph` on the repository method:
```java
@EntityGraph(attributePaths = {"employee", "employee.department"})
List<Payslip> findAll();
```

---

## Version 6 — Performance Reviews & Analytics

### Issue 1: Reporting Query Timeout on Large Datasets
**Problem:** Aggregation queries for analytics time out with large employee counts.
**Solution:** Add database indexes on frequently filtered columns (`department_id`, `created_at`, `status`). Use pagination and projections instead of loading full entities.

### Issue 2: Circular Dependency on `ReviewService` and `EmployeeService`
**Problem:** `ReviewService` autowires `EmployeeService` and vice versa for notification logic.
**Solution:** Extract notification logic into a separate `NotificationService` to break the cycle, or use `@Lazy` injection as a temporary fix.

### Issue 3: Chart Data Aggregation Timezone Issues
**Problem:** Analytics grouped by month shift by one month for users in different timezones.
**Solution:** Store all dates in UTC. Convert to user's timezone only at the API response level using `ZonedDateTime` or accept a `timezone` query parameter.

---

## Version 7 — Admin Dashboard, Polish & Deployment

### Issue 1: Spring Boot Actuator Endpoints Exposed in Production
**Problem:** `/actuator/env` and `/actuator/beans` expose sensitive configuration.
**Solution:** Restrict actuator endpoints in production config:
```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
```

### Issue 2: Docker Build Fails — `JAVA_HOME` Not Set
**Problem:** Multi-stage Docker build can't find Java 21.
**Solution:** Use the official `eclipse-temurin:21-jdk-alpine` as builder and `eclipse-temurin:21-jre-alpine` as runtime stage:
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Issue 3: React Build `ENOENT` Error for Environment Variables
**Problem:** Vite/CRA build fails because `REACT_APP_API_URL` is not defined.
**Solution:** Create `.env.production` with the correct API URL and ensure it is included in CI/CD pipeline secrets, not committed to the repo.

### Issue 4: PostgreSQL Container Not Ready Before App Container
**Problem:** App container starts before PostgreSQL is ready in `docker-compose`, causing startup failure.
**Solution:** Use `healthcheck` and `depends_on` with `condition: service_healthy` in `docker-compose.yml`:
```yaml
depends_on:
  postgres:
    condition: service_healthy
```

### Issue 5: HTTPS Mixed Content Errors After Deployment
**Problem:** React app served over HTTPS makes HTTP API calls — blocked by browser.
**Solution:** Configure a reverse proxy (Nginx) to forward all `/api` requests over HTTPS, or ensure the backend is also served on HTTPS.

---

*Last Updated: Version 7 Complete*
