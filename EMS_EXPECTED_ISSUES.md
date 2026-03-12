# Employee Management System — Expected Issues (Proactive Risk Analysis)

> This document anticipates problems *before* they happen, organized by version.
> Think of this as a pre-mortem: what could go wrong and how to handle it in advance.

---

## Version 1 — Project Setup & Basic CRUD

### 1. Jakarta EE Namespace Confusion
**Risk:** Developers unfamiliar with Spring Boot 4 will write `import javax.persistence.*` from muscle memory. The entire project will fail to compile with confusing errors like `package javax.persistence does not exist`.
**Prevention:** Add a `checkstyle` rule or a README note at the top of every entity file. Run a quick `grep -r "javax\." src/` before each commit.

### 2. PostgreSQL Not Running Locally
**Risk:** Developers clone the repo and hit `Connection refused` immediately. This is the #1 frustration for new contributors.
**Prevention:** Add a `docker-compose.dev.yml` with just PostgreSQL so anyone can run `docker-compose -f docker-compose.dev.yml up -d` to get a DB instantly, no local installation needed.

### 3. Forgetting to Create the Database Schema
**Risk:** PostgreSQL is running, but `ems_db` database doesn't exist. Spring Boot throws `FATAL: database "ems_db" does not exist`.
**Prevention:** Add a one-liner in the README: `psql -U postgres -c "CREATE DATABASE ems_db;"` or use a Docker init script that creates the DB automatically.

### 4. `ddl-auto=create-drop` Left On in Dev
**Risk:** A developer accidentally uses `create-drop` in their local dev config. Every restart wipes all data. Hours of test data entry lost.
**Prevention:** Default to `ddl-auto=update` in `application.properties` (dev) and `ddl-auto=validate` in production. Use Flyway or Liquibase for proper schema migration.

### 5. Lombok Not Generating Code in IDE
**Risk:** IDE shows errors on `@Data`-annotated classes even though Maven builds fine. Developers waste time thinking the code is broken.
**Prevention:** Document in README: "Enable annotation processing in your IDE." IntelliJ: `Preferences → Build → Compiler → Annotation Processors → Enable`.

### 6. Returning JPA Entity Directly (Without DTO)
**Risk:** If entities are returned directly and you later add `@JsonIgnore` or change a field name, it silently breaks API contracts. Lazy-loaded collections also cause `LazyInitializationException` outside a transaction.
**Prevention:** Establish the DTO pattern from day one. Never return raw entities from controllers.

### 7. Pagination Not Implemented from the Start
**Risk:** The employee list returns all records. Initially fine with 10 employees, catastrophic with 10,000.
**Prevention:** Use `Pageable` from the very first version. It's much harder to retrofit pagination onto an existing API without breaking client code.

---

## Version 2 — Authentication & JWT Security

### 1. JWT Secret Hardcoded in Source Code
**Risk:** Developers commit `jwt.secret=mysecretkey123` to GitHub. The secret is now public and all tokens can be forged by anyone.
**Prevention:** Use environment variables: `jwt.secret=${JWT_SECRET}`. Add `.env` to `.gitignore`. Use a minimum 256-bit secret in production.

### 2. Tokens Never Expire or Expire Too Quickly
**Risk:** `expirationMs=86400000` (24h) might be too long for sensitive data. But `expirationMs=300000` (5 min) will frustrate users who get logged out during form filling.
**Prevention:** Use 15-minute access tokens + long-lived refresh tokens. Implement a `/api/auth/refresh` endpoint from the start.

### 3. No Token Invalidation on Logout
**Risk:** JWT is stateless — once issued, it's valid until expiry even after "logout". An attacker who steals a token can use it until it expires.
**Prevention:** Maintain a server-side blocklist (Redis set) of invalidated token JTIs. Check against it in `JwtAuthFilter`. Or accept this limitation explicitly and document it.

### 4. Missing `OPTIONS` in CORS Config
**Risk:** Browser sends a preflight `OPTIONS` request before `POST`/`PUT`. If CORS config doesn't allow `OPTIONS`, every mutation fails with a CORS error — confusing because GET works fine.
**Prevention:** Add `OPTIONS` to `allowedMethods` in CORS config. In Spring Security, call `http.cors(withDefaults())` to apply the CORS config.

### 5. Encoding Passwords Before They Reach the Service
**Risk:** A developer accidentally encodes the password in the controller AND in the service, resulting in double-hashed passwords that can never be verified.
**Prevention:** Hash passwords only in `AuthService.register()`, immediately before saving to the DB. Never hash in controllers or other layers.

### 6. Role Enum Stored as Ordinal
**Risk:** `ROLE_ADMIN` is stored as `0`, `ROLE_USER` as `1`. Adding a new role at the beginning shifts all ordinals, corrupting all existing role data.
**Prevention:** Always use `@Enumerated(EnumType.STRING)`. This stores `"ROLE_ADMIN"` as a string — immune to reordering.

### 7. Security Config Accidentally Blocking H2 Console
**Risk:** In test/dev, the H2 console (`/h2-console`) is blocked by Spring Security, making database inspection impossible.
**Prevention:** Explicitly permit `/h2-console/**` in dev profile's security config and disable `frameOptions` (H2 console uses iframes).

---

## Version 3 — Department & Role Management

### 1. Infinite Recursion in JSON Serialization
**Risk:** `Department` → `List<Employee>` → `Employee.department` → `Department` → infinite loop. Jackson throws `StackOverflowError` or hits the cycle detection limit.
**Prevention:** Use DTOs (the real fix), or annotate with `@JsonManagedReference` / `@JsonBackReference`. Never return raw bidirectional JPA entities.

### 2. Deleting a Department Still Referenced by Employees
**Risk:** `DELETE /api/departments/5` succeeds, but employees still have `department_id = 5`, which now points to nothing. Foreign key constraint will prevent this if configured, but you may get an opaque DB error.
**Prevention:** Check if the department has employees before deleting. Throw a meaningful `BusinessException("Department has X active employees. Reassign them first.")`.

### 3. Manager Being Their Own Manager
**Risk:** The employee form allows selecting any employee as the manager, including themselves. This creates a cycle in the hierarchy.
**Prevention:** Validate in the service: `if (managerId.equals(employeeId)) throw new BadRequestException("Employee cannot manage themselves")`. Also ensure no circular chains (A manages B, B manages A).

### 4. Search Query with Empty String Returning All Records
**Risk:** `GET /api/employees?search=` passes an empty string, and `WHERE name LIKE '%%'` matches everything — potentially thousands of records.
**Prevention:** If `search` param is blank or null, skip the LIKE clause entirely. Always pair with pagination.

### 5. MapStruct Not Regenerating After Entity Change
**Risk:** You add a new field to `Employee` entity. MapStruct mapper was compiled before the change. The new field silently gets `null` in the DTO.
**Prevention:** After any entity change, run `mvn compile` to regenerate MapStruct. Add a unit test that asserts every field of the DTO is non-null for a fully populated entity.

---

## Version 4 — Leave Management

### 1. Timezone Ambiguity in Leave Dates
**Risk:** An employee in India submits a leave for `2025-03-15`. The server is in UTC. Depending on how the date is serialized, the server may store `2025-03-14` (the previous day in UTC-5:30).
**Prevention:** Use `LocalDate` (no timezone) for leave dates since a "day off" is always calendar-date-relative, not a moment in time. Never use `Date` or `Timestamp` for leave.

### 2. Leave Balance Going Negative
**Risk:** Concurrent approval of two leave requests could both read balance = 2, both deduct 2, resulting in balance = -2.
**Prevention:** Use `@Version` (optimistic locking) on `LeaveBalance`. The second concurrent update will throw `OptimisticLockingFailureException` and the transaction will be retried or rejected.

### 3. Leave Request Editable After Approval
**Risk:** An employee edits a leave request after it's been approved, changing the dates. The manager sees the original dates but the system has the new ones.
**Prevention:** Once a leave is `APPROVED` or `REJECTED`, reject any update attempts with `409 Conflict`. Only `PENDING` leaves should be editable/cancellable.

### 4. Weekend and Public Holiday Not Excluded from Leave Count
**Risk:** An employee takes a 5-day leave spanning a weekend. The system deducts 5 days from their balance, but technically only 3 working days were taken.
**Prevention:** Implement a `WorkingDaysCalculator` utility that counts only Monday-Friday (and skips a configurable list of public holidays). This is complex but critical for correctness.

### 5. Annual Leave Balance Reset Job Fails Silently
**Risk:** The `@Scheduled` job runs at midnight on Jan 1 but throws an exception. There's no alerting — employees start the new year with 0 balance and don't notice until they try to book leave.
**Prevention:** Wrap the scheduler in `try-catch`, log errors to a monitoring system (or send an admin notification), and provide a manual trigger endpoint `POST /api/admin/leave/reset-balances` as a fallback.

### 6. Email Notification Blocking the Request Thread
**Risk:** Leave approval sends an email synchronously. If the mail server is slow or down, the `/approve` endpoint hangs for 30 seconds.
**Prevention:** Use `@Async` to send emails on a separate thread pool, or use a message queue (RabbitMQ/Kafka) for notifications. Return the API response immediately.

---

## Version 5 — Payroll & Salary Management

### 1. Payroll Run for Wrong Month
**Risk:** Admin clicks "Generate Payroll" for January when it's March. Employees receive wrong-month payslips. There's no way to "un-generate" without direct DB manipulation.
**Prevention:** Add a confirmation step in the UI and a clear month/year selector with the current month pre-selected. Require explicit confirmation for past-month runs.

### 2. Salary Changed Mid-Month Affects Current Payroll
**Risk:** An employee gets a raise on March 15. Payroll generated for March uses the new salary for the full month, overpaying by 15 days.
**Prevention:** Use `SalaryRevision` with effective dates. Payroll calculation checks what salary was active from the 1st of the month.

### 3. PDF Generation Memory Leak
**Risk:** iText/PDFBox creates large in-memory objects. With 500 employees, generating all payslips at once can exhaust heap memory.
**Prevention:** Generate PDFs lazily — only when a user downloads one. For bulk payroll reports, use streaming with `StreamingResponseBody` and process employees in batches.

### 4. Tax Calculation Bugs Going Unnoticed
**Risk:** An off-by-one in the tax slab logic over-deducts by a small amount from every payslip. With 500 employees over 12 months, this is a significant compliance issue.
**Prevention:** Write thorough parameterized unit tests for `TaxCalculator` covering edge cases: minimum wage, slab boundaries, zero salary, maximum salary.

### 5. Payslip Accessible by Wrong Employee
**Risk:** `GET /api/payroll/123` — Employee A with ID 45 can access Employee B's payslip (ID 123) by just changing the URL.
**Prevention:** In the service layer, always verify: `if (!payslip.getEmployee().getId().equals(currentUserId) && !currentUser.isAdmin()) throw new AccessDeniedException()`. Never trust URL parameters for ownership checks.

---

## Version 6 — Performance Reviews & Analytics

### 1. Analytics Queries Locking Up the Database
**Risk:** A heavy aggregation query (salary distribution across all employees + all departments) runs a full table scan. On a shared DB, this blocks other queries.
**Prevention:** Run analytics queries on a read replica. Add `@QueryHint` with `QUERY_TIMEOUT`. Cache results with `@Cacheable` (Spring Cache + Redis) since analytics rarely need real-time freshness.

### 2. Performance Review Submitted for Terminated Employee
**Risk:** The reviewer selects an ex-employee who left the company. The review is saved, but the employee no longer has access to see it.
**Prevention:** Add an `active` flag to `Employee`. Filter the reviewer's employee dropdown to only show active employees.

### 3. Rating Scale Inconsistency Across Review Periods
**Risk:** Reviews in Q1 used a 5-point scale; Q2 used a 10-point scale after a policy change. Analytics comparing ratings across periods are meaningless.
**Prevention:** Store both the `rating` value and the `scale` (max possible rating). Normalize to a percentage for cross-period comparisons.

### 4. Chart Data Not Refreshing After New Data Is Added
**Risk:** Admin generates payroll, then looks at analytics — the chart still shows last month's data because the response is cached.
**Prevention:** Set appropriate `Cache-Control` headers on analytics endpoints. Invalidate the cache when underlying data changes (e.g., after payroll generation).

### 5. Large CSV Export Timing Out
**Risk:** Exporting analytics for 3 years of data for 1,000 employees generates a 50MB CSV. The request times out at the Nginx proxy level (default 60s timeout).
**Prevention:** Implement as an async job: `POST /api/analytics/export` → returns `jobId`. Poll `GET /api/analytics/export/{jobId}/status`. Download when ready. Alternatively, stream using `StreamingResponseBody`.

---

## Version 7 — Deployment & Production

### 1. Secrets Committed to GitHub
**Risk:** Developer commits `application-prod.properties` with real DB credentials to the repo. Credentials are now in git history forever (even if the file is deleted later).
**Prevention:** Use `git-secrets` or GitHub's secret scanning. Keep all credentials in CI/CD environment variables or a secrets manager (AWS Secrets Manager, HashiCorp Vault). Immediately rotate any exposed credentials.

### 2. Docker Image Runs as Root
**Risk:** The app container runs as `root`. If the app is compromised, the attacker has root inside the container — making container escape easier.
**Prevention:** Add `USER appuser` to Dockerfile after creating a non-root user:
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

### 3. Database Not Backed Up Before Schema Migration
**Risk:** A Flyway migration script runs on the production database and has a bug — it drops a column that still had data. The data is gone.
**Prevention:** Always take a `pg_dump` backup before running any migration in production. Test the migration on a staging environment first.

### 4. Health Check Endpoint Reveals Version Info
**Risk:** `/actuator/info` returns `{"version": "1.2.3", "java": {"version": "21.0.1"}}`. Attackers know exactly what vulnerabilities to probe.
**Prevention:** Disable the `info` endpoint in production, or carefully control what it reveals. Only expose `health` (without details) and `metrics` to authenticated monitoring systems.

### 5. Single Point of Failure — No Horizontal Scaling Plan
**Risk:** The application runs as a single Docker container. Any restart causes downtime. As the company grows, one instance can't handle the load.
**Prevention:** Design stateless from day one (no in-memory session state, no local file storage). Use Redis for shared state (caching, rate limiting). Deploy behind a load balancer. Use Kubernetes or Docker Swarm for orchestration.

### 6. CORS Configuration Too Permissive in Production
**Risk:** `allowedOrigins("*")` was used during development and accidentally left in production config. Any website can make authenticated requests on behalf of your users.
**Prevention:** In `application-prod.properties`, set `cors.allowed-origins=${ALLOWED_ORIGINS}` and inject the exact production frontend URL. Never use `*` with `allowCredentials(true)` — this is actually blocked by browsers but a config smell.

### 7. React App Calling HTTP in an HTTPS Environment
**Risk:** React is served over HTTPS. `REACT_APP_API_URL=http://api.company.com`. Browser blocks mixed content — the app silently stops working.
**Prevention:** Ensure both frontend and backend use HTTPS in production. Or configure Nginx to proxy `/api` to the backend so both share the same origin (no CORS needed at all).

### 8. No Logging Strategy — Debugging Production Issues Is Impossible
**Risk:** A bug occurs in production but all logs show is `NullPointerException` with no context about which employee, which endpoint, which input caused it.
**Prevention:** Use structured logging (Logback + SLF4J). Log at `WARN`/`ERROR` with the full context (endpoint, user ID, request body summary). Integrate with a log aggregation tool (ELK stack, Datadog, Grafana Loki).

### 9. No Rate Limiting Causes Credential Stuffing
**Risk:** An attacker sends 10,000 login attempts to `/api/auth/login` in 10 seconds, trying common passwords. No rate limiting — they can brute-force weak passwords.
**Prevention:** Implement rate limiting on auth endpoints specifically: max 10 attempts per IP per minute. Use Bucket4j or Spring's `spring-boot-starter-security` throttling support. Add account lockout after N failed attempts.

### 10. Test Data in Production Database
**Risk:** During initial setup, developers seed the DB with fake employees for testing. The "dummy" data is never cleaned up and ends up in the live system.
**Prevention:** Use `@Profile("dev")` on all `DataInitializer` / `CommandLineRunner` beans that insert test data. These beans will never run in production.

---

## Cross-Cutting Concerns (All Versions)

### Code Quality Risks
- **No code reviews:** Bugs that a second pair of eyes would catch make it to production.
- **Tests skipped to "save time":** Technical debt compounds; by v5 the codebase is untestable.
- **No `.editorconfig`:** Inconsistent indentation and line endings across team members cause noisy diffs.

### Git Workflow Risks
- **Committing directly to `master`:** One bad push breaks everyone. Use feature branches and pull requests.
- **No commit message convention:** After v3, the git log is `fix stuff`, `test`, `asdf`, making debugging impossible.
- **Large binary files committed:** Someone commits a `.pdf` sample payslip or a `.sql` dump. Git history grows by hundreds of MB.

### Dependency Risks
- **Not pinning dependency versions:** `mvn versions:display-dependency-updates` shows updates. Blindly upgrading to latest can break the build.
- **Transitive dependency conflicts:** Two libraries pull in different versions of the same class. Manifests as `NoSuchMethodError` at runtime.
- **Outdated dependencies with CVEs:** Using old versions of Jackson, Spring Security, or jjwt with known vulnerabilities.

---

*Last Updated: Proactive risk analysis for all 7 versions*
