# EMS API Testing Guide — Version 1 (Employee CRUD)

**Base URL:** `http://localhost:8080`
**Content-Type:** `application/json`

---

## How to Start the Server

```bash
cd C:\Users\Admin\Desktop\Spring\EMS\backend
mvn spring-boot:run
```

Server starts on `http://localhost:8080`

---

## 1. Create Employee — POST /api/employees

**Method:** `POST`
**URL:** `http://localhost:8080/api/employees`
**Headers:** `Content-Type: application/json`

### Test Case 1.1 — Success (201 Created)
**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "9876543210",
  "hireDate": "2024-01-15",
  "position": "Software Engineer",
  "salary": 75000
}
```
**Expected Response: 201 Created**
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "9876543210",
  "hireDate": "2024-01-15",
  "position": "Software Engineer",
  "salary": 75000,
  "createdAt": "2026-03-12T...",
  "updatedAt": "2026-03-12T..."
}
```

---

### Test Case 1.2 — Create Second Employee (for list/search tests)
**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "phone": "9123456789",
  "hireDate": "2024-03-01",
  "position": "HR Manager",
  "salary": 65000
}
```
**Expected Response: 201 Created**

---

### Test Case 1.3 — Create Third Employee
**Request Body:**
```json
{
  "firstName": "Alice",
  "lastName": "Brown",
  "email": "alice.brown@example.com",
  "phone": "9000011111",
  "hireDate": "2023-06-10",
  "position": "DevOps Engineer",
  "salary": 80000
}
```
**Expected Response: 201 Created**

---

### Test Case 1.4 — Duplicate Email (409 Conflict)
**Request Body:**
```json
{
  "firstName": "Another",
  "lastName": "Person",
  "email": "john.doe@example.com",
  "salary": 50000
}
```
**Expected Response: 409 Conflict**
```json
{
  "status": 409,
  "message": "Email already in use: john.doe@example.com",
  "timestamp": "..."
}
```

---

### Test Case 1.5 — Missing Required Fields (400 Bad Request)
**Request Body:**
```json
{
  "firstName": "NoEmail"
}
```
**Expected Response: 400 Bad Request**
```json
{
  "email": "Email is required",
  "lastName": "Last name is required"
}
```

---

### Test Case 1.6 — Invalid Email Format (400 Bad Request)
**Request Body:**
```json
{
  "firstName": "Bad",
  "lastName": "Email",
  "email": "not-an-email",
  "salary": 50000
}
```
**Expected Response: 400 Bad Request**
```json
{
  "email": "Invalid email format"
}
```

---

### Test Case 1.7 — Negative Salary (400 Bad Request)
**Request Body:**
```json
{
  "firstName": "Bad",
  "lastName": "Salary",
  "email": "bad.salary@example.com",
  "salary": -5000
}
```
**Expected Response: 400 Bad Request**
```json
{
  "salary": "Salary must be non-negative"
}
```

---

## 2. Get All Employees — GET /api/employees

**Method:** `GET`

### Test Case 2.1 — Get All (Default Pagination)
**URL:** `http://localhost:8080/api/employees`

**Expected Response: 200 OK**
```json
{
  "content": [
    { "id": 1, "firstName": "John", ... },
    { "id": 2, "firstName": "Jane", ... },
    { "id": 3, "firstName": "Alice", ... }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 3,
  "totalPages": 1,
  "last": true
}
```

---

### Test Case 2.2 — Pagination (Page 0, Size 2)
**URL:** `http://localhost:8080/api/employees?page=0&size=2`

**Expected Response: 200 OK**
```json
{
  "content": [ {...}, {...} ],
  "page": 0,
  "size": 2,
  "totalElements": 3,
  "totalPages": 2,
  "last": false
}
```

---

### Test Case 2.3 — Pagination (Page 1, Size 2)
**URL:** `http://localhost:8080/api/employees?page=1&size=2`

**Expected Response: 200 OK**
```json
{
  "content": [ {...} ],
  "page": 1,
  "size": 2,
  "totalElements": 3,
  "totalPages": 2,
  "last": true
}
```

---

### Test Case 2.4 — Sort by Salary Descending
**URL:** `http://localhost:8080/api/employees?sortBy=salary&sortDir=desc`

**Expected Response: 200 OK**
First employee in `content` should have highest salary (Alice — 80000).

---

### Test Case 2.5 — Search by First Name
**URL:** `http://localhost:8080/api/employees?search=John`

**Expected Response: 200 OK**
```json
{
  "content": [ { "firstName": "John", "lastName": "Doe", ... } ],
  "totalElements": 1
}
```

---

### Test Case 2.6 — Search by Last Name
**URL:** `http://localhost:8080/api/employees?search=Smith`

**Expected Response: 200 OK** — returns Jane Smith only.

---

### Test Case 2.7 — Search by Email Partial
**URL:** `http://localhost:8080/api/employees?search=alice`

**Expected Response: 200 OK** — returns Alice Brown only.

---

### Test Case 2.8 — Search with No Match
**URL:** `http://localhost:8080/api/employees?search=ZZZNOMATCH`

**Expected Response: 200 OK**
```json
{
  "content": [],
  "totalElements": 0
}
```

---

## 3. Get Employee By ID — GET /api/employees/{id}

**Method:** `GET`

### Test Case 3.1 — Get Existing Employee
**URL:** `http://localhost:8080/api/employees/1`

**Expected Response: 200 OK**
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "9876543210",
  "hireDate": "2024-01-15",
  "position": "Software Engineer",
  "salary": 75000.00
}
```

---

### Test Case 3.2 — Get Non-Existent Employee (404 Not Found)
**URL:** `http://localhost:8080/api/employees/9999`

**Expected Response: 404 Not Found**
```json
{
  "status": 404,
  "message": "Employee not found with id: 9999",
  "timestamp": "..."
}
```

---

## 4. Update Employee — PUT /api/employees/{id}

**Method:** `PUT`
**Headers:** `Content-Type: application/json`

### Test Case 4.1 — Update All Fields (200 OK)
**URL:** `http://localhost:8080/api/employees/1`
**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "9999999999",
  "hireDate": "2024-01-15",
  "position": "Senior Software Engineer",
  "salary": 90000
}
```
**Expected Response: 200 OK**
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "position": "Senior Software Engineer",
  "salary": 90000.00,
  ...
}
```

---

### Test Case 4.2 — Update Email to Already Used Email (409 Conflict)
**URL:** `http://localhost:8080/api/employees/1`
**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "jane.smith@example.com",
  "salary": 90000
}
```
**Expected Response: 409 Conflict**
```json
{
  "status": 409,
  "message": "Email already in use: jane.smith@example.com"
}
```

---

### Test Case 4.3 — Update Non-Existent Employee (404 Not Found)
**URL:** `http://localhost:8080/api/employees/9999`
**Request Body:**
```json
{
  "firstName": "Ghost",
  "lastName": "User",
  "email": "ghost@example.com",
  "salary": 50000
}
```
**Expected Response: 404 Not Found**

---

### Test Case 4.4 — Update with Invalid Data (400 Bad Request)
**URL:** `http://localhost:8080/api/employees/1`
**Request Body:**
```json
{
  "firstName": "",
  "lastName": "Doe",
  "email": "invalid-email",
  "salary": 50000
}
```
**Expected Response: 400 Bad Request**
```json
{
  "firstName": "First name is required",
  "email": "Invalid email format"
}
```

---

## 5. Delete Employee — DELETE /api/employees/{id}

**Method:** `DELETE`

### Test Case 5.1 — Delete Existing Employee (204 No Content)
**URL:** `http://localhost:8080/api/employees/3`

**Expected Response: 204 No Content** (empty body)

**Verify deletion:**
`GET http://localhost:8080/api/employees/3` → should return **404 Not Found**

---

### Test Case 5.2 — Delete Non-Existent Employee (404 Not Found)
**URL:** `http://localhost:8080/api/employees/9999`

**Expected Response: 404 Not Found**
```json
{
  "status": 404,
  "message": "Employee not found with id: 9999"
}
```

---

## 6. Full Flow Test (Run in Order)

| Step | Method | URL | Expected Status |
|------|--------|-----|-----------------|
| 1 | POST | `/api/employees` | 201 |
| 2 | POST | `/api/employees` (same email) | 409 |
| 3 | GET | `/api/employees` | 200 |
| 4 | GET | `/api/employees/1` | 200 |
| 5 | GET | `/api/employees/9999` | 404 |
| 6 | PUT | `/api/employees/1` | 200 |
| 7 | GET | `/api/employees?search=John` | 200 |
| 8 | GET | `/api/employees?page=0&size=1` | 200 |
| 9 | DELETE | `/api/employees/1` | 204 |
| 10 | GET | `/api/employees/1` | 404 |

---

## Postman Quick Import

Create a Postman collection with these requests. Set a variable `{{baseUrl}} = http://localhost:8080`.

| Request Name | Method | URL |
|---|---|---|
| Create Employee | POST | `{{baseUrl}}/api/employees` |
| Get All Employees | GET | `{{baseUrl}}/api/employees` |
| Get All - Paginated | GET | `{{baseUrl}}/api/employees?page=0&size=2` |
| Get All - Search | GET | `{{baseUrl}}/api/employees?search=John` |
| Get All - Sort | GET | `{{baseUrl}}/api/employees?sortBy=salary&sortDir=desc` |
| Get Employee by ID | GET | `{{baseUrl}}/api/employees/1` |
| Get Employee - 404 | GET | `{{baseUrl}}/api/employees/9999` |
| Update Employee | PUT | `{{baseUrl}}/api/employees/1` |
| Delete Employee | DELETE | `{{baseUrl}}/api/employees/1` |

---

*Version 1 — Employee CRUD only (no authentication required)*
