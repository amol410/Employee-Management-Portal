# EMS API Testing Guide — Version 5 (Payroll & Salary Management)

**Base URL:** `http://localhost:8080`
**Auth:** All requests need `Authorization: Bearer <token>` except public auth endpoints.

---

## New in V5
- `POST /api/payroll/generate` — generate DRAFT payroll for employee (ADMIN)
- `PATCH /api/payroll/{id}/process` — move DRAFT → PROCESSED (ADMIN)
- `PATCH /api/payroll/{id}/pay` — move PROCESSED → PAID (ADMIN)
- `GET /api/payroll` — all payrolls, filter by `?month=&year=` (ADMIN/MANAGER)
- `GET /api/payroll/{id}` — single payroll record (any auth)
- `GET /api/payroll/employee/{employeeId}` — payrolls for an employee (any auth)
- `POST /api/payroll/salary-revisions` — revise salary + log history (ADMIN)
- `GET /api/payroll/salary-revisions` — all revisions (ADMIN/MANAGER)
- `GET /api/payroll/salary-revisions/employee/{employeeId}` — revisions for employee
- Net pay = `basicSalary + allowances - deductions`
- Payroll workflow: `DRAFT → PROCESSED → PAID`

---

## Setup — Get Tokens + Seed Data

```bash
# Login as admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Login as employee
EMP_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"empuser","password":"emp123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Create employee (salary=80000)
EMP_ID=$(curl -s -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Alice","lastName":"Dev","email":"alice@co.com","salary":80000}' \
  | grep -o '"id":[0-9]*' | cut -d: -f2)

# Create manager employee
MGR_ID=$(curl -s -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Bob","lastName":"Boss","email":"bob@co.com","salary":120000}' \
  | grep -o '"id":[0-9]*' | cut -d: -f2)

echo "EMP_ID=$EMP_ID  MGR_ID=$MGR_ID"
```

---

## 1. Payroll Generation

### T01 — Generate Payroll as ADMIN (201)
```bash
curl -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":3,\"payYear\":2026,\"allowances\":5000,\"deductions\":3000,\"notes\":\"March 2026\"}"
```
**Expected: 201**
```json
{
  "id": 1,
  "employeeId": 1,
  "employeeName": "Alice Dev",
  "payMonth": 3,
  "payYear": 2026,
  "basicSalary": 80000,
  "allowances": 5000,
  "deductions": 3000,
  "netPay": 82000,
  "status": "DRAFT"
}
```
Save ID:
```bash
PAYROLL_ID=<id from response>
```

### T02 — Net Pay Verification
```
Net Pay = basicSalary (80000) + allowances (5000) - deductions (3000) = 82000
```

### T03 — Generate as EMPLOYEE (403)
```bash
curl -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":4,\"payYear\":2026}"
```
**Expected: 403**

### T04 — Duplicate Payroll (409)
```bash
curl -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":3,\"payYear\":2026}"
```
**Expected: 409** — `{"message":"Payroll already exists for employee X for 3/2026"}`

### T05 — Invalid Month (400)
```bash
curl -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":13,\"payYear\":2026}"
```
**Expected: 400** — `{"payMonth":"Month must be 1-12"}`

---

## 2. Payroll Workflow

### T06 — Process Payroll: DRAFT → PROCESSED (200)
```bash
curl -X PATCH "http://localhost:8080/api/payroll/$PAYROLL_ID/process" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — `"status":"PROCESSED"`, `processedAt` is set

### T07 — Process Already-Processed (409)
```bash
curl -X PATCH "http://localhost:8080/api/payroll/$PAYROLL_ID/process" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 409** — `{"message":"Only DRAFT payrolls can be processed"}`

### T08 — Mark Paid from DRAFT (skip process) (409)
```bash
# Generate new payroll then try to pay directly
NEW_PAY=$(curl -s -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":5,\"payYear\":2026}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

curl -X PATCH "http://localhost:8080/api/payroll/$NEW_PAY/pay" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 409** — `{"message":"Only PROCESSED payrolls can be marked as PAID"}`

### T09 — Full Workflow: DRAFT → PROCESSED → PAID (200)
```bash
# Process
curl -X PATCH "http://localhost:8080/api/payroll/$NEW_PAY/process" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Mark paid
curl -X PATCH "http://localhost:8080/api/payroll/$NEW_PAY/pay" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — `"status":"PAID"`, `paidAt` is set

---

## 3. Payroll Read Endpoints

### T10 — Get Payroll by ID (200)
```bash
curl "http://localhost:8080/api/payroll/$PAYROLL_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Full payroll details

### T11 — Get Payroll Not Found (404)
```bash
curl "http://localhost:8080/api/payroll/9999" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 404**

### T12 — Get All Payrolls as ADMIN (200)
```bash
curl "http://localhost:8080/api/payroll" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Paged response

### T13 — Get All Payrolls as EMPLOYEE (403)
```bash
curl "http://localhost:8080/api/payroll" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 403**

### T14 — Filter by Month/Year (200)
```bash
curl "http://localhost:8080/api/payroll?month=3&year=2026" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Only March 2026 payrolls

### T15 — Get Payrolls for Employee (200)
```bash
curl "http://localhost:8080/api/payroll/employee/$EMP_ID" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** — All payrolls for Alice

---

## 4. Salary Revisions

### T16 — Revise Salary as ADMIN (201)
```bash
curl -X POST http://localhost:8080/api/payroll/salary-revisions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"newSalary\":90000,\"effectiveDate\":\"2026-04-01\",\"reason\":\"Annual increment\",\"revisedById\":$MGR_ID}"
```
**Expected: 201**
```json
{
  "id": 1,
  "employeeName": "Alice Dev",
  "oldSalary": 80000,
  "newSalary": 90000,
  "changeAmount": 10000,
  "changePercent": 12.5,
  "effectiveDate": "2026-04-01",
  "reason": "Annual increment",
  "revisedByName": "Bob Boss"
}
```

### T17 — Employee Salary Updated After Revision
```bash
curl "http://localhost:8080/api/employees/$EMP_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — `"salary":90000`

### T18 — New Payroll Uses Updated Salary
```bash
curl -X POST http://localhost:8080/api/payroll/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"payMonth\":4,\"payYear\":2026}"
```
**Expected: 201** — `"basicSalary":90000`

### T19 — Revise Salary as EMPLOYEE (403)
```bash
curl -X POST http://localhost:8080/api/payroll/salary-revisions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"newSalary\":100000,\"effectiveDate\":\"2026-04-01\"}"
```
**Expected: 403**

### T20 — Get Salary Revisions for Employee (200)
```bash
curl "http://localhost:8080/api/payroll/salary-revisions/employee/$EMP_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — History with changeAmount and changePercent

### T21 — Get All Salary Revisions (200)
```bash
curl "http://localhost:8080/api/payroll/salary-revisions" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — All revisions across all employees

---

## 5. Permission Matrix

| Endpoint | EMPLOYEE | MANAGER | ADMIN |
|---|---|---|---|
| POST /api/payroll/generate | ❌ 403 | ❌ 403 | ✅ 201 |
| PATCH /api/payroll/{id}/process | ❌ 403 | ❌ 403 | ✅ 200 |
| PATCH /api/payroll/{id}/pay | ❌ 403 | ❌ 403 | ✅ 200 |
| GET /api/payroll | ❌ 403 | ✅ 200 | ✅ 200 |
| GET /api/payroll/{id} | ✅ 200 | ✅ 200 | ✅ 200 |
| GET /api/payroll/employee/{id} | ✅ 200 | ✅ 200 | ✅ 200 |
| POST /api/payroll/salary-revisions | ❌ 403 | ❌ 403 | ✅ 201 |
| GET /api/payroll/salary-revisions | ❌ 403 | ✅ 200 | ✅ 200 |
| GET /api/payroll/salary-revisions/employee/{id} | ✅ 200 | ✅ 200 | ✅ 200 |

---

## 6. Full Flow Test

| Step | Action | Expected |
|------|--------|----------|
| 1 | POST /api/payroll/generate (Mar 2026) | 201, DRAFT, netPay=82000 |
| 2 | POST /api/payroll/generate (Mar 2026 again) | 409 |
| 3 | PATCH /api/payroll/{id}/process | 200, PROCESSED |
| 4 | PATCH /api/payroll/{id}/process again | 409 |
| 5 | PATCH /api/payroll/{id}/pay | 200, PAID, paidAt set |
| 6 | POST /api/payroll/salary-revisions (80000→90000) | 201, changePercent=12.5 |
| 7 | GET /api/employees/{id} | 200, salary=90000 |
| 8 | POST /api/payroll/generate (Apr 2026) | 201, basicSalary=90000 |
| 9 | GET /api/payroll?month=3&year=2026 | 200, 1 result |
| 10 | GET /api/payroll/salary-revisions/employee/{id} | 200, 1 revision |

---

*Version 5 — Payroll & Salary Management added. All V1–V4 endpoints still work.*
