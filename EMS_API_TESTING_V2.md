# EMS API Testing Guide — Version 2 (JWT Authentication & Security)

**Base URL:** `http://localhost:8080`
**Content-Type:** `application/json`

> All employee endpoints now require a valid JWT token.
> Save the token from register/login and use it as `Authorization: Bearer <token>` header.

---

## How to Start the Server

```bash
cd C:\Users\Admin\Desktop\Spring\EMS\backend
mvn spring-boot:run
```

---

## 1. Register — POST /api/auth/register

**Method:** `POST`
**URL:** `http://localhost:8080/api/auth/register`
**Auth Required:** No

### Test Case 1.1 — Register Admin User (201 Created)
```json
{
  "username": "admin",
  "email": "admin@ems.com",
  "password": "admin123"
}
```
**Expected Response: 201 Created**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "email": "admin@ems.com",
  "role": "ROLE_EMPLOYEE"
}
```
> Note: All registered users start as `ROLE_EMPLOYEE`. To make someone an admin, update directly in MySQL:
> ```sql
> UPDATE users SET role = 'ROLE_ADMIN' WHERE username = 'admin';
> ```
> Then login again to get a fresh token with the new role.

---

### Test Case 1.2 — Register Regular Employee (201 Created)
```json
{
  "username": "johndoe",
  "email": "john.doe@ems.com",
  "password": "password123"
}
```
**Expected Response: 201 Created** — save this token as `EMPLOYEE_TOKEN`

---

### Test Case 1.3 — Register Manager (201 Created)
```json
{
  "username": "manager1",
  "email": "manager@ems.com",
  "password": "manager123"
}
```
> After registering, set role in DB:
> ```sql
> UPDATE users SET role = 'ROLE_MANAGER' WHERE username = 'manager1';
> ```

---

### Test Case 1.4 — Duplicate Username (409 Conflict)
```json
{
  "username": "admin",
  "email": "other@ems.com",
  "password": "admin123"
}
```
**Expected Response: 409 Conflict**
```json
{
  "status": 409,
  "message": "Username already taken: admin"
}
```

---

### Test Case 1.5 — Duplicate Email (409 Conflict)
```json
{
  "username": "newuser",
  "email": "admin@ems.com",
  "password": "admin123"
}
```
**Expected Response: 409 Conflict**
```json
{
  "status": 409,
  "message": "Email already in use: admin@ems.com"
}
```

---

### Test Case 1.6 — Short Password (400 Bad Request)
```json
{
  "username": "testuser",
  "email": "test@ems.com",
  "password": "123"
}
```
**Expected Response: 400 Bad Request**
```json
{
  "password": "Password must be at least 6 characters"
}
```

---

### Test Case 1.7 — Short Username (400 Bad Request)
```json
{
  "username": "ab",
  "email": "test@ems.com",
  "password": "validpass"
}
```
**Expected Response: 400 Bad Request**
```json
{
  "username": "Username must be between 3 and 50 characters"
}
```

---

## 2. Login — POST /api/auth/login

**Method:** `POST`
**URL:** `http://localhost:8080/api/auth/login`
**Auth Required:** No

### Test Case 2.1 — Successful Login (200 OK)
```json
{
  "username": "admin",
  "password": "admin123"
}
```
**Expected Response: 200 OK**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "email": "admin@ems.com",
  "role": "ROLE_ADMIN"
}
```
> Save this token — you'll use it as `ADMIN_TOKEN` for all secured requests.

---

### Test Case 2.2 — Wrong Password (401 Unauthorized)
```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```
**Expected Response: 401 Unauthorized**
```json
{
  "status": 401,
  "message": "Invalid username or password"
}
```

---

### Test Case 2.3 — Non-Existent User (401 Unauthorized)
```json
{
  "username": "nobody",
  "password": "doesntmatter"
}
```
**Expected Response: 401 Unauthorized**

---

### Test Case 2.4 — Missing Fields (400 Bad Request)
```json
{
  "username": "admin"
}
```
**Expected Response: 400 Bad Request**
```json
{
  "password": "Password is required"
}
```

---

## 3. Employee Endpoints (Now Secured)

> For all requests below, add this header:
> ```
> Authorization: Bearer <your_token_here>
> ```

---

### Test Case 3.1 — Create Employee as ADMIN (201 Created)
**Method:** `POST` | **URL:** `http://localhost:8080/api/employees`
**Header:** `Authorization: Bearer <ADMIN_TOKEN>`
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@company.com",
  "phone": "9876543210",
  "hireDate": "2024-01-15",
  "position": "Software Engineer",
  "salary": 75000
}
```
**Expected: 201 Created**

---

### Test Case 3.2 — Create Employee as EMPLOYEE (403 Forbidden)
**Header:** `Authorization: Bearer <EMPLOYEE_TOKEN>`
Same body as above (different email).

**Expected: 403 Forbidden**
> Regular employees cannot create/update/delete other employees.

---

### Test Case 3.3 — Create Employee with No Token (401 Unauthorized)
No `Authorization` header.

**Expected: 401 Unauthorized**

---

### Test Case 3.4 — Get All Employees as EMPLOYEE (200 OK)
**Method:** `GET` | **URL:** `http://localhost:8080/api/employees`
**Header:** `Authorization: Bearer <EMPLOYEE_TOKEN>`

**Expected: 200 OK** — any authenticated user can read employees.

---

### Test Case 3.5 — Get All Employees with No Token (401 Unauthorized)
**Method:** `GET` | **URL:** `http://localhost:8080/api/employees`
No header.

**Expected: 401 Unauthorized**

---

### Test Case 3.6 — Update Employee as MANAGER (200 OK)
**Method:** `PUT` | **URL:** `http://localhost:8080/api/employees/1`
**Header:** `Authorization: Bearer <MANAGER_TOKEN>`
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@company.com",
  "position": "Senior Software Engineer",
  "salary": 90000
}
```
**Expected: 200 OK** — Managers can update employees.

---

### Test Case 3.7 — Delete Employee as ADMIN (204 No Content)
**Method:** `DELETE` | **URL:** `http://localhost:8080/api/employees/1`
**Header:** `Authorization: Bearer <ADMIN_TOKEN>`

**Expected: 204 No Content**

---

### Test Case 3.8 — Delete Employee as MANAGER (403 Forbidden)
**Method:** `DELETE` | **URL:** `http://localhost:8080/api/employees/2`
**Header:** `Authorization: Bearer <MANAGER_TOKEN>`

**Expected: 403 Forbidden** — Only ADMIN can delete.

---

## 4. Token Expiry & Invalid Token Tests

### Test Case 4.1 — Tampered Token (401 Unauthorized)
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.TAMPERED.signature
```
`GET http://localhost:8080/api/employees`

**Expected: 401 Unauthorized**

---

### Test Case 4.2 — Wrong Format (401 Unauthorized)
```
Authorization: NotBearer sometoken
```
**Expected: 401 Unauthorized**

---

## 5. Role Permission Matrix

| Endpoint | ROLE_EMPLOYEE | ROLE_MANAGER | ROLE_ADMIN |
|---|---|---|---|
| POST /api/auth/register | ✅ Public | ✅ Public | ✅ Public |
| POST /api/auth/login | ✅ Public | ✅ Public | ✅ Public |
| GET /api/employees | ✅ 200 | ✅ 200 | ✅ 200 |
| POST /api/employees | ❌ 403 | ✅ 201 | ✅ 201 |
| PUT /api/employees/{id} | ❌ 403 | ✅ 200 | ✅ 200 |
| DELETE /api/employees/{id} | ❌ 403 | ❌ 403 | ✅ 204 |
| No token (any endpoint) | ❌ 401 | ❌ 401 | ❌ 401 |

---

## 6. Full Flow Test (Run in Order)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Register `admin` | 201 + token |
| 2 | Set `admin` role = ROLE_ADMIN in DB | — |
| 3 | Login as `admin` | 200 + new token |
| 4 | Register `empuser` | 201 + token |
| 5 | GET /api/employees with no token | 401 |
| 6 | GET /api/employees with empuser token | 200 |
| 7 | POST /api/employees with empuser token | 403 |
| 8 | POST /api/employees with admin token | 201 |
| 9 | DELETE /api/employees/1 with empuser token | 403 |
| 10 | DELETE /api/employees/1 with admin token | 204 |

---

## Postman Setup

1. Create a collection variable `{{baseUrl}} = http://localhost:8080`
2. Create variables `{{adminToken}}` and `{{employeeToken}}`
3. After login, copy the token into the variable
4. Add header `Authorization: Bearer {{adminToken}}` to secured requests

---

*Version 2 — JWT Auth added. All v1 endpoints still work (now require token).*
