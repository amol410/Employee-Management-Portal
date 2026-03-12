# Employee Management System — Interview Questions (Version by Version)

---

## Version 1 — Project Setup & Basic Employee CRUD

### Core Java & Spring
1. What is the difference between `@Component`, `@Service`, `@Repository`, and `@Controller`? Why does Spring provide all four if they are all `@Component`?
2. Explain the Spring IoC container and how dependency injection works. What are the three types of injection?
3. What is the difference between `CrudRepository`, `JpaRepository`, and `PagingAndSortingRepository`? Which did you use and why?
4. What does `@Transactional` do? What is the difference between `@Transactional` on a class vs a method?
5. What is the N+1 problem in JPA? How does it manifest and how do you fix it?

### REST API Design
6. What is the difference between `PUT` and `PATCH`? Which did you use for employee updates and why?
7. What HTTP status codes did you return for: successful creation, not found, validation failure, server error?
8. What is a `@RestControllerAdvice`? How does it differ from `@ControllerAdvice`?
9. How did you implement pagination in the employee list endpoint? What query parameters does the client send?
10. What is the purpose of a DTO? Why not return the entity directly from the controller?

### Database & JPA
11. What is the difference between `ddl-auto=update` and `ddl-auto=create-drop`? Which should you use in production?
12. What annotations define an entity's primary key? What is `@GeneratedValue(strategy = GenerationType.IDENTITY)`?
13. How does H2 in-memory database help in testing? Why is it used instead of a real PostgreSQL instance in tests?
14. What is Lombok? What does `@Data` generate? What are the risks of using `@EqualsAndHashCode` on JPA entities?
15. What is MapStruct? How does it differ from doing manual mapping? What does the generated code look like?

---

## Version 2 — Authentication & JWT Security

### Spring Security
1. What is `SecurityFilterChain`? How does it differ from the old `WebSecurityConfigurerAdapter`?
2. Explain the request processing flow when a JWT request hits your Spring Boot application. Which filters are involved?
3. What is `SecurityContextHolder`? What is its default storage strategy and why?
4. What is the difference between `Authentication` and `Authorization`?
5. Why is CSRF protection disabled in a stateless JWT API? When should it be enabled?

### JWT
6. What are the three parts of a JWT? What does each contain?
7. What is the difference between signing (`HS256`) and encrypting a JWT? Which did you use?
8. How do you handle JWT expiration? What happens when a token expires mid-session?
9. What are the security risks of storing JWT in `localStorage` vs `httpOnly` cookie?
10. How would you implement JWT refresh tokens? What endpoint would handle token renewal?

### Security Concepts
11. Why should you never store plain-text passwords? What does BCrypt do differently from MD5/SHA?
12. What is a `UserDetailsService`? What method must it implement?
13. What is CORS? Why does the browser block requests from `localhost:3000` to `localhost:8080`? How did you fix it?
14. What is the difference between `permitAll()`, `authenticated()`, and `hasRole()` in Spring Security?
15. How did you implement role-based access? What is `@PreAuthorize` and how does it compare to URL-based security?

---

## Version 3 — Department & Role Management

### JPA Relationships
1. Explain `@OneToMany` and `@ManyToOne`. What is the "owning side" of a relationship?
2. What is the difference between `FetchType.LAZY` and `FetchType.EAGER`? What are the trade-offs?
3. How do you prevent the `StackOverflowError` caused by bidirectional JPA relationships during JSON serialization?
4. What is `CascadeType.ALL` and what operations does it include? Is it safe to use on all relationships?
5. What is orphan removal in JPA? When would you use `orphanRemoval = true`?

### Query & Design
6. How did you write a custom JPQL query to count employees per department? What does `@Query` do?
7. What is the difference between JPQL and native SQL queries in Spring Data JPA?
8. Why did you return a `departmentName` string in `EmployeeDTO` instead of a full `DepartmentDTO`?
9. How does MapStruct handle nested object mapping? What is `@Mapping(source = "department.name", target = "departmentName")`?
10. How did you implement search functionality (by name/email)? What is `%:query%` in a JPQL `LIKE` clause?

### Architecture
11. What is the Service Layer pattern? Why should controllers not directly call repositories?
12. How do you handle the case where a requested resource does not exist? What exception do you throw?
13. What is `@Valid` vs `@Validated`? Where do you place them in a controller method signature?
14. How do you prevent deleting a department that has employees assigned to it? Where does this logic live — service or DB constraint?
15. What is the difference between a `RuntimeException` and a checked exception? Why do most Spring apps use unchecked exceptions?

---

## Version 4 — Leave Management

### Business Logic
1. How did you implement date overlap validation for leave requests? What JPQL query detects overlapping ranges?
2. What is optimistic locking? How does `@Version` prevent race conditions in leave balance deduction?
3. Why is it important to check leave balance before approving a request rather than after?
4. What state machine governs a `LeaveRequest`? What transitions are valid (e.g., can an APPROVED leave be rejected)?
5. How did you handle the `@Scheduled` job for annual leave balance reset? What cron expression runs it on January 1st?

### Spring & Java
6. What is `LocalDate` vs `LocalDateTime` vs `ZonedDateTime`? Which did you use for leave dates and why?
7. What is `@JsonFormat` and when would you use it on a `LocalDate` field?
8. How do you configure Jackson to serialize `LocalDate` as `"yyyy-MM-dd"` instead of an array `[2025, 1, 15]`?
9. What is `@PreAuthorize("@leaveService.isOwner(#id, principal.username)")`? What is SpEL?
10. What is the difference between `@Scheduled(cron = "...")` and `@Scheduled(fixedRate = ...)`?

### Testing
11. How do you test a Spring `@Scheduled` method without waiting for it to fire naturally?
12. How do you mock the current date in a unit test for time-sensitive business logic?
13. What is `@MockBean` in Spring tests? How does it differ from Mockito's `@Mock`?
14. How did you test that overlap validation correctly rejects a conflicting leave request?
15. How do you test authorization — ensuring a regular employee cannot approve leave requests?

---

## Version 5 — Payroll & Salary Management

### Java & Precision
1. Why should you never use `double` or `float` for financial calculations? What is the problem with `0.1 + 0.2` in floating point?
2. How does `BigDecimal` solve precision issues? What is `RoundingMode.HALF_UP` and when is it appropriate?
3. What is the difference between `BigDecimal("0.10")` and `BigDecimal(0.10)`? Why does the second form lose precision?
4. How did you calculate net salary? Walk through the formula including PF deduction and income tax slab logic.
5. What is idempotency? How did you ensure payroll generation for the same month is idempotent?

### Spring & JPA
6. What unique constraint did you add to prevent duplicate payslips? How is it defined in JPA?
7. What is `DataIntegrityViolationException`? How did you catch and convert it to a meaningful API error?
8. How did you implement PDF generation? What library did you use and what are its key classes?
9. What is the `@EntityGraph` annotation? How did it solve the N+1 problem on the payslip list?
10. How does Spring Data Auditing work? What annotations auto-populate `createdAt`, `updatedAt`, `createdBy`?

### Design
11. What is the difference between `SalaryRevision` as an audit log vs just updating `Employee.salary`?
12. How would you design the payroll system to handle mid-month salary revisions (prorated salary)?
13. What are the security considerations for the payroll module? Who should be able to generate, view, and download payslips?
14. What is a projection in Spring Data JPA? How is it different from returning a full entity?
15. What is `@Transactional(readOnly = true)` and what performance benefit does it provide?

---

## Version 6 — Performance Reviews & Analytics

### Analytics & Query Optimization
1. What is a JPQL projection interface? How does it avoid loading full entities for analytics queries?
2. What SQL aggregate functions did your analytics queries use? Write a query to count employees per department.
3. What is a database index? Which columns did you index in this version and why?
4. What is the difference between `GROUP BY` and `PARTITION BY` in SQL?
5. How did you implement the "salary distribution by department" analytics endpoint?

### Architecture & Design
6. What is the Single Responsibility Principle? How does extracting `NotificationService` follow it?
7. How did you break the circular dependency between `ReviewService` and `EmployeeService`?
8. What is `@Lazy` injection? Is it a good long-term solution for circular dependencies?
9. What is a DTO projection vs a full entity? When does returning an entity from a service become a problem?
10. How do you handle timezone issues in analytics grouped by month? What does storing dates in UTC achieve?

### Frontend & Integration
11. What is Recharts? How did you pass backend aggregation data into a bar chart component?
12. How did you implement CSV export on the frontend? Did the backend generate it or the frontend?
13. What is a React Context? How did you use it for the analytics date range filter state?
14. How do you prevent a non-admin user from accessing the analytics endpoints? Where is this enforced — backend, frontend, or both?
15. Why must security always be enforced on the backend even if the frontend hides sensitive UI elements?

---

## Version 7 — Admin Dashboard, Notifications, Polish & Deployment

### Deployment & DevOps
1. What is a multi-stage Docker build? What is the benefit of separating the build stage from the runtime stage?
2. What is the difference between `CMD` and `ENTRYPOINT` in a Dockerfile?
3. What is Docker Compose? How does `depends_on` with `condition: service_healthy` solve startup ordering?
4. What is a reverse proxy? How did you configure Nginx to forward `/api` requests to the Spring Boot backend?
5. What is a GitHub Actions workflow? What triggers and steps did your CI pipeline have?

### Spring Boot Production
6. What is Spring Boot Actuator? Which endpoints did you expose in production and why were others disabled?
7. What is rate limiting? How does Bucket4j implement token-bucket rate limiting in Spring Boot?
8. What is Spring Data Auditing? What are `@CreatedBy` and `@LastModifiedBy`? How does `AuditorAware` work?
9. What is `@ConditionalOnProperty`? How did you use it to disable the payroll scheduler in tests?
10. What are environment variable placeholders in `application.properties` (`${DB_PASSWORD}`)? Why is this important for security?

### System Design
11. How does the notification system work end-to-end? What triggers a notification and how does the frontend poll for them?
12. What are the trade-offs between polling for notifications vs WebSocket push notifications?
13. How would you scale this application horizontally (multiple instances)? What problems arise with JWT, sessions, and scheduled jobs?
14. What is connection pooling? What pool does Spring Boot use by default and why is it important?
15. If 10,000 employees use this system simultaneously, what are the likely bottlenecks and how would you address them?

### General Project Questions
16. Walk me through the entire architecture of this system — from the React frontend to the database.
17. What was the most difficult bug you encountered across all versions? How did you debug and resolve it?
18. If you were to add a "Documents Upload" feature (store CVs, contracts), how would you implement it?
19. How would you implement soft delete across all entities instead of hard delete?
20. What would you do differently if you started this project over?

---

*Last Updated: Version 7 Complete*
