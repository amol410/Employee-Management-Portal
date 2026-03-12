# Employee Management System — Progress & Features (Version by Version)

---

## Version 1 — Project Scaffolding & Basic Employee CRUD
**Goal:** Establish the project foundation with a working backend and frontend skeleton.

### Backend Features
- Spring Boot 4.0.3 project initialized via Spring Initializr with:
  - Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Validation
- `Employee` entity with fields: `id`, `firstName`, `lastName`, `email`, `phone`, `hireDate`, `position`, `salary`
- `EmployeeRepository` extending `JpaRepository`
- `EmployeeService` with full CRUD methods
- `EmployeeController` exposing REST endpoints:
  - `GET /api/employees` — list all (paginated)
  - `GET /api/employees/{id}` — get by ID
  - `POST /api/employees` — create
  - `PUT /api/employees/{id}` — update
  - `DELETE /api/employees/{id}` — delete
- `EmployeeDTO` + MapStruct mapper for request/response separation
- Bean Validation on DTOs (`@NotBlank`, `@Email`, `@Positive`)
- Global exception handler (`@RestControllerAdvice`) for `404` and validation errors
- `application.properties` configured for PostgreSQL
- H2 test database configured in `src/test/resources/`

### Frontend Features
- React app created with Vite + Tailwind CSS configured
- Basic project structure: `pages/`, `components/`, `services/`
- Axios instance configured with `baseURL` pointing to backend
- Employee list page: table showing all employees
- Add Employee form with basic input validation
- Edit Employee form (pre-filled)
- Delete confirmation dialog
- Basic responsive layout with Tailwind utility classes

### Testing
- Unit tests for `EmployeeService` (Mockito)
- Integration tests for `EmployeeController` (MockMvc + H2)

### GitHub
- Repository initialized, `.gitignore` configured for Java + Node
- First commit: project scaffold
- Tag: `v1.0`

---

## Version 2 — Authentication & Authorization (JWT)
**Goal:** Secure the API with JWT-based authentication and role-based access control.

### Backend Features
- `User` entity with fields: `id`, `username`, `email`, `password`, `role`
- `Role` enum: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EMPLOYEE`
- `AuthController` with:
  - `POST /api/auth/register` — register new user
  - `POST /api/auth/login` — authenticate and return JWT
- `JwtUtil` — token generation, validation, claims extraction
- `JwtAuthFilter` — intercepts requests, validates token, populates `SecurityContext`
- `SecurityConfig` — `SecurityFilterChain` bean with:
  - Public routes: `/api/auth/**`
  - Protected routes: all others require authentication
  - Role-specific access on employee write endpoints (ADMIN/MANAGER only)
- `UserDetailsServiceImpl` loading users from DB
- `BCryptPasswordEncoder` bean for password hashing
- CORS configured to allow React dev server (`localhost:3000`)

### Frontend Features
- Login page with email/password form
- Register page
- JWT token stored in `localStorage`
- Axios interceptor to attach `Authorization: Bearer <token>` header on every request
- `AuthContext` (React Context) for global auth state
- `ProtectedRoute` component redirecting unauthenticated users to login
- Logout button clearing token and redirecting to login
- Role-aware UI: hide add/edit/delete buttons from non-admin users

### Testing
- Unit tests for `JwtUtil`
- Integration tests for `AuthController` (register + login flows)
- Security tests asserting 401 on unauthenticated access

### GitHub
- Branch: `feature/auth` → merged to `master`
- Tag: `v2.0`

---

## Version 3 — Department & Role Management
**Goal:** Organize employees into departments and introduce manager hierarchy.

### Backend Features
- `Department` entity: `id`, `name`, `description`, `createdAt`
- `Employee` updated: added `@ManyToOne Department` and `managerId` reference
- `DepartmentController`:
  - `GET /api/departments` — list all
  - `GET /api/departments/{id}` — get with employee count
  - `POST /api/departments` — create (ADMIN only)
  - `PUT /api/departments/{id}` — update (ADMIN only)
  - `DELETE /api/departments/{id}` — delete if no employees (ADMIN only)
- `EmployeeDTO` updated to include `departmentName`, `managerName`
- MapStruct mappings updated for nested objects
- Custom JPQL queries:
  - Employees by department
  - Department employee count
  - Search employees by name/email

### Frontend Features
- Department management page (list, add, edit, delete)
- Employee form updated: department dropdown populated from API
- Employee list: filter by department
- Search bar for employee name/email
- `DepartmentBadge` component with color coding per department
- Manager assignment in employee form (dropdown of managers)

### Testing
- Tests for department CRUD
- Tests for employee-department relationship endpoints
- Tests for search and filter functionality

### GitHub
- Branch: `feature/departments` → merged to `master`
- Tag: `v3.0`

---

## Version 4 — Leave Management
**Goal:** Allow employees to request leaves and managers/admins to approve or reject them.

### Backend Features
- `LeaveRequest` entity: `id`, `employee`, `leaveType`, `startDate`, `endDate`, `reason`, `status`, `reviewedBy`, `reviewedAt`
- `LeaveType` enum: `ANNUAL`, `SICK`, `UNPAID`, `MATERNITY`, `PATERNITY`
- `LeaveStatus` enum: `PENDING`, `APPROVED`, `REJECTED`
- `LeaveBalance` entity per employee per leave type (annual allocation)
- `LeaveController`:
  - `POST /api/leaves` — submit request (authenticated employee)
  - `GET /api/leaves/my` — own leave history
  - `GET /api/leaves` — all requests (MANAGER/ADMIN)
  - `PUT /api/leaves/{id}/approve` — approve (MANAGER/ADMIN)
  - `PUT /api/leaves/{id}/reject` — reject with reason (MANAGER/ADMIN)
- Overlap validation: prevent duplicate/overlapping date ranges
- `LeaveBalance` auto-decremented on approval
- Spring `@Scheduled` job to reset annual leave balances on Jan 1

### Frontend Features
- Employee dashboard with leave balance summary cards
- Leave request form: date picker, leave type selector, reason textarea
- Leave history table with status badges (color-coded)
- Manager view: pending requests queue with approve/reject buttons
- Admin view: all requests filterable by employee/department/status/date range
- Leave calendar view (monthly grid showing who is on leave)

### Testing
- Tests for leave submission, approval, rejection
- Tests for overlap validation
- Tests for balance deduction
- Tests for unauthorized access to approval endpoints

### GitHub
- Branch: `feature/leaves` → merged to `master`
- Tag: `v4.0`

---

## Version 5 — Payroll & Salary Management
**Goal:** Automate monthly payroll generation with deductions and payslip history.

### Backend Features
- `Payslip` entity: `id`, `employee`, `month`, `year`, `basicSalary`, `bonuses`, `deductions`, `netSalary`, `generatedAt`
- `SalaryRevision` entity: tracks salary change history per employee
- `PayrollController`:
  - `POST /api/payroll/generate` — generate payroll for a given month (ADMIN)
  - `GET /api/payroll/my` — employee's own payslips
  - `GET /api/payroll/{employeeId}` — payslips for specific employee (ADMIN)
  - `GET /api/payroll/{id}/download` — download payslip as PDF
- `PayrollService` calculates:
  - Basic salary from `Employee.salary`
  - Deductions: PF (12%), tax slab-based income tax
  - Bonuses: performance-based (manual input)
  - Net salary = basic + bonuses - deductions
- `BigDecimal` used throughout for precision
- Idempotent generation: unique constraint on `(employee_id, month, year)`
- `@Scheduled` auto-generation on the last working day of the month
- PDF export using iText or Apache PDFBox

### Frontend Features
- Admin payroll dashboard: generate payroll by month/year with one click
- Payslip list per employee with month/year filter
- Payslip detail view: breakdown of earnings and deductions
- Download payslip as PDF button
- Employee dashboard: "My Payslips" section
- Salary revision history page (admin view)

### Testing
- Tests for payroll calculation accuracy
- Tests for idempotent generation
- Tests for unauthorized payslip access
- Tests for salary revision tracking

### GitHub
- Branch: `feature/payroll` → merged to `master`
- Tag: `v5.0`

---

## Version 6 — Performance Reviews & Analytics Dashboard
**Goal:** Enable manager-led performance reviews and provide data-driven analytics.

### Backend Features
- `PerformanceReview` entity: `id`, `employee`, `reviewer`, `period`, `rating`, `comments`, `goals`, `createdAt`
- `Rating` enum: `EXCELLENT`, `GOOD`, `AVERAGE`, `BELOW_AVERAGE`, `POOR`
- `ReviewController`:
  - `POST /api/reviews` — create review (MANAGER/ADMIN)
  - `GET /api/reviews/employee/{id}` — review history per employee
  - `GET /api/reviews/my` — own reviews (employee view)
  - `PUT /api/reviews/{id}` — update review (before finalization)
- Analytics endpoints:
  - `GET /api/analytics/headcount` — total/active/by-department
  - `GET /api/analytics/turnover` — monthly hire vs exit trend
  - `GET /api/analytics/leave-utilization` — leave usage by type/department
  - `GET /api/analytics/salary-distribution` — salary ranges by department
- All analytics use JPQL aggregation queries with projections (no full entity loads)

### Frontend Features
- Performance review form for managers
- Employee review history timeline view
- Star rating component with color mapping
- Analytics dashboard with 4 chart widgets:
  - Headcount bar chart by department (Recharts)
  - Turnover trend line chart (monthly)
  - Leave utilization donut chart
  - Salary distribution histogram
- Date range picker for analytics filters
- Export analytics to CSV

### Testing
- Tests for review CRUD and authorization
- Tests for each analytics endpoint with sample data
- Tests for date range filtering

### GitHub
- Branch: `feature/analytics` → merged to `master`
- Tag: `v6.0`

---

## Version 7 — Admin Dashboard, Notifications, Polish & Deployment
**Goal:** Complete the portal with a unified admin view, notifications, and production deployment.

### Backend Features
- `Notification` entity: `id`, `recipient`, `type`, `message`, `isRead`, `createdAt`
- `NotificationService` triggered on:
  - Leave approved/rejected
  - Payslip generated
  - Performance review submitted
- `NotificationController`:
  - `GET /api/notifications` — get own unread notifications
  - `PUT /api/notifications/{id}/read` — mark as read
  - `PUT /api/notifications/read-all` — mark all as read
- Spring Boot Actuator configured (health, info, metrics — production-safe)
- API rate limiting via Bucket4j
- Full audit logging (`@CreatedBy`, `@LastModifiedBy` via Spring Data Auditing)
- `application-prod.properties` with environment variable placeholders

### Frontend Features
- Unified Admin Dashboard: KPI summary cards + recent activity feed
- Notification bell in navbar with unread count badge
- Notification dropdown: recent notifications with mark-as-read
- Global search bar: search employees, departments across the portal
- Dark mode toggle (Tailwind `dark:` classes)
- Breadcrumb navigation
- Loading skeletons on all data-fetch components
- 404 and error boundary pages
- Fully responsive mobile layout

### Infrastructure & Deployment
- `Dockerfile` (multi-stage build) for backend
- `Dockerfile` for frontend (Nginx serving built React app)
- `docker-compose.yml` with services: `backend`, `frontend`, `postgres`
- `nginx.conf` proxying `/api` to backend, serving React on `/`
- GitHub Actions CI pipeline:
  - On push to `master`: run tests, build Docker images, push to Docker Hub
- Environment variables managed via `.env` (not committed)

### Testing
- Tests for notification creation and retrieval
- End-to-end smoke test hitting all major endpoints
- Docker Compose integration test: services boot and health check passes

### GitHub
- Branch: `feature/final-polish` → merged to `master`
- Tag: `v7.0` — production release

---

## Summary Table

| Version | Focus Area                          | Key Entities Added                    | Endpoints Added |
|---------|-------------------------------------|---------------------------------------|-----------------|
| v1      | CRUD Foundation                     | Employee                              | 5               |
| v2      | Auth & Security                     | User                                  | 2 + security    |
| v3      | Departments & Hierarchy             | Department                            | 5 + search      |
| v4      | Leave Management                    | LeaveRequest, LeaveBalance            | 6               |
| v5      | Payroll & Salary                    | Payslip, SalaryRevision               | 5 + PDF         |
| v6      | Reviews & Analytics                 | PerformanceReview                     | 4 + 4 analytics |
| v7      | Notifications, Polish & Deployment  | Notification                          | 3 + infra       |

---

*Last Updated: Version 7 Complete*
