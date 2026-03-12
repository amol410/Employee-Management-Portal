# EMS API Testing Guide — Version 3 (Department & Manager Hierarchy)

**Base URL:** `http://localhost:8080`
**Auth:** All requests need `Authorization: Bearer <token>` except public auth endpoints.

---

## New in V3
- `POST/PUT/DELETE /api/departments` — department management
- `GET /api/departments` / `GET /api/departments/{id}` — read departments (any auth)
- `GET /api/employees?departmentId=1` — filter employees by department
- Employee now has `departmentId`, `departmentName`, `managerId`, `managerName` in response

---

## Setup — Get Tokens
```bash
# Login as admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Login as employee
EMP_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"empuser","password":"emp123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
```

---

## 1. Department CRUD

### T01 — Create Department as ADMIN (201)
```bash
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Engineering","description":"Software development team"}'
```
**Expected: 201** `{"id":1,"name":"Engineering","employeeCount":0,...}`

### T02 — Create Department as EMPLOYEE (403)
```bash
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d '{"name":"HR"}'
```
**Expected: 403**

### T03 — Create Without Token (401)
```bash
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -d '{"name":"Finance"}'
```
**Expected: 401**

### T04 — Duplicate Department Name (409)
```bash
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Engineering"}'
```
**Expected: 409**

### T05 — Missing Name (400)
```bash
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"description":"No name here"}'
```
**Expected: 400** `{"name":"Department name is required"}`

### T06 — Get All Departments (200)
```bash
curl http://localhost:8080/api/departments \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** Array of departments with `employeeCount`

### T07 — Get Department by ID (200)
```bash
curl http://localhost:8080/api/departments/1 \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** `{"id":1,"name":"Engineering","employeeCount":0,...}`

### T08 — Get Department Not Found (404)
```bash
curl http://localhost:8080/api/departments/9999 \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 404**

### T09 — Update Department (200)
```bash
curl -X PUT http://localhost:8080/api/departments/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Engineering","description":"Updated description"}'
```
**Expected: 200**

### T10 — Delete Empty Department (204)
```bash
curl -X DELETE http://localhost:8080/api/departments/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 204** (only works if dept has 0 employees)

---

## 2. Employee + Department Integration

### T11 — Create Employee in Department (201)
```bash
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Alice","lastName":"Dev","email":"alice@co.com","salary":80000,"departmentId":1}'
```
**Expected: 201** Response includes `"departmentId":1,"departmentName":"Engineering"`

### T12 — Department Shows Employee Count
```bash
curl http://localhost:8080/api/departments/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** `"employeeCount":1`

### T13 — Filter Employees by Department
```bash
curl "http://localhost:8080/api/employees?departmentId=1" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** Only employees in department 1

### T14 — Delete Department with Employees (409)
```bash
curl -X DELETE http://localhost:8080/api/departments/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 409** `{"message":"Cannot delete department with 1 active employee(s). Reassign them first."}`

### T15 — Employee Response Has Department Name
```bash
curl http://localhost:8080/api/employees/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** `"departmentName":"Engineering"` in response

---

## 3. Manager Assignment

### T16 — Assign Manager to Employee (200)
```bash
# First create a manager employee
MGR_ID=$(curl -s -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Senior","lastName":"Manager","email":"mgr@co.com","salary":100000}' \
  | grep -o '"id":[0-9]*' | cut -d: -f2)

# Assign manager
curl -X PUT http://localhost:8080/api/employees/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"firstName\":\"Alice\",\"lastName\":\"Dev\",\"email\":\"alice@co.com\",\"salary\":80000,\"managerId\":$MGR_ID}"
```
**Expected: 200** Response includes `"managerId":<id>,"managerName":"Senior Manager"`

### T17 — Employee Response Shows Manager Name
```bash
curl http://localhost:8080/api/employees/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** `"managerName":"Senior Manager"`

### T18 — Self-Manager Validation (400)
```bash
curl -X PUT http://localhost:8080/api/employees/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Alice","lastName":"Dev","email":"alice@co.com","salary":80000,"managerId":1}'
```
**Expected: 400** `{"message":"Employee cannot be their own manager"}`

---

## 4. Permission Matrix

| Endpoint | EMPLOYEE | MANAGER | ADMIN |
|---|---|---|---|
| GET /api/departments | ✅ 200 | ✅ 200 | ✅ 200 |
| POST /api/departments | ❌ 403 | ❌ 403 | ✅ 201 |
| PUT /api/departments/{id} | ❌ 403 | ✅ 200 | ✅ 200 |
| DELETE /api/departments/{id} | ❌ 403 | ❌ 403 | ✅ 204 |

---

## 5. Full Flow Test

| Step | Action | Expected |
|------|--------|----------|
| 1 | POST /api/departments (Engineering) | 201 |
| 2 | POST /api/departments (HR) | 201 |
| 3 | POST /api/departments (Engineering again) | 409 |
| 4 | GET /api/departments | 200, 2 items |
| 5 | POST /api/employees with departmentId | 201 |
| 6 | GET /api/departments/1 | 200, employeeCount=1 |
| 7 | GET /api/employees?departmentId=1 | 200, totalElements=1 |
| 8 | DELETE /api/departments/1 | 409 (has employees) |
| 9 | PUT employee, set managerId | 200 |
| 10 | GET employee | 200, managerName visible |
| 11 | PUT employee, set managerId=self | 400 |
| 12 | DELETE /api/departments/2 (empty HR) | 204 |

---

*Version 3 — Department hierarchy added. All V1+V2 endpoints still work.*
