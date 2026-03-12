# EMS API Testing Guide — Version 4 (Leave Management)

**Base URL:** `http://localhost:8080`
**Auth:** All requests need `Authorization: Bearer <token>` except public auth endpoints.

---

## New in V4
- `POST /api/leaves/balances` — set leave balance for employee (ADMIN only)
- `GET /api/leaves/balances/{employeeId}?year=2026` — get balances
- `POST /api/leaves/apply` — apply for leave (any authenticated user)
- `PATCH /api/leaves/{id}/approve` — approve leave (ADMIN/MANAGER)
- `PATCH /api/leaves/{id}/reject` — reject leave (ADMIN/MANAGER)
- `PATCH /api/leaves/{id}/cancel?employeeId=X` — cancel own leave
- `GET /api/leaves` — all leave requests, optional `?status=PENDING` (ADMIN/MANAGER)
- `GET /api/leaves/{id}` — single leave request
- `GET /api/leaves/employee/{employeeId}` — leaves for an employee
- Leave types: `ANNUAL`, `SICK`, `CASUAL`, `MATERNITY`, `PATERNITY`, `UNPAID`
- Working days only counted (Mon–Fri)
- Balance: `totalDays - usedDays - pendingDays = remainingDays`

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

# Create an employee (if not exists from v3)
EMP_ID=$(curl -s -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Alice","lastName":"Dev","email":"alice@co.com","salary":80000}' \
  | grep -o '"id":[0-9]*' | cut -d: -f2)

# Create a manager employee
MGR_ID=$(curl -s -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"firstName":"Bob","lastName":"Boss","email":"bob@co.com","salary":100000}' \
  | grep -o '"id":[0-9]*' | cut -d: -f2)

echo "EMP_ID=$EMP_ID  MGR_ID=$MGR_ID"
```

---

## 1. Leave Balance Management

### T01 — Set Balance as ADMIN (201)
```bash
curl -X POST http://localhost:8080/api/leaves/balances \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"ANNUAL\",\"year\":2026,\"totalDays\":20}"
```
**Expected: 201**
```json
{"id":1,"employeeId":1,"employeeName":"Alice Dev","leaveType":"ANNUAL","year":2026,"totalDays":20,"usedDays":0,"pendingDays":0,"remainingDays":20}
```

### T02 — Set Balance as EMPLOYEE (403)
```bash
curl -X POST http://localhost:8080/api/leaves/balances \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"SICK\",\"year\":2026,\"totalDays\":10}"
```
**Expected: 403**

### T03 — Set Multiple Leave Types
```bash
curl -X POST http://localhost:8080/api/leaves/balances \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"SICK\",\"year\":2026,\"totalDays\":10}"

curl -X POST http://localhost:8080/api/leaves/balances \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"CASUAL\",\"year\":2026,\"totalDays\":7}"
```
**Expected: Both 201**

### T04 — Get Balances for Employee (200)
```bash
curl "http://localhost:8080/api/leaves/balances/$EMP_ID?year=2026" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Array with 3 leave types (ANNUAL, SICK, CASUAL)

---

## 2. Apply for Leave

### T05 — Apply ANNUAL Leave (201)
```bash
curl -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"ANNUAL\",\"startDate\":\"2026-04-07\",\"endDate\":\"2026-04-11\",\"reason\":\"Spring vacation\"}"
```
**Expected: 201**
```json
{"id":1,"employeeId":1,"employeeName":"Alice Dev","leaveType":"ANNUAL","startDate":"2026-04-07","endDate":"2026-04-11","daysRequested":5,"status":"PENDING",...}
```
> Note: 2026-04-07 (Mon) to 2026-04-11 (Fri) = 5 working days

Save the leave ID:
```bash
LEAVE_ID=<id from response>
```

### T06 — Insufficient Balance (422)
```bash
# Try to apply 25 days when only 20 available (15 remaining after T05)
curl -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"ANNUAL\",\"startDate\":\"2026-05-04\",\"endDate\":\"2026-06-05\"}"
```
**Expected: 422** — `{"message":"Insufficient balance. Requested: X, Available: 15"}`

### T07 — End Date Before Start Date (400)
```bash
curl -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"ANNUAL\",\"startDate\":\"2026-06-10\",\"endDate\":\"2026-06-05\"}"
```
**Expected: 400** — `{"message":"End date cannot be before start date"}`

### T08 — Apply Without Balance (422)
```bash
curl -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"MATERNITY\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-05\"}"
```
**Expected: 422** — No balance configured for MATERNITY

### T09 — Apply UNPAID Leave (no balance needed) (201)
```bash
curl -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"UNPAID\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-02\"}"
```
**Expected: 201** — UNPAID type skips balance check

### T10 — Balance Updates After Apply (pendingDays increases)
```bash
curl "http://localhost:8080/api/leaves/balances/$EMP_ID?year=2026" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — ANNUAL: `pendingDays:5`, `remainingDays:15`

---

## 3. Approve / Reject

### T11 — Approve Leave as ADMIN (200)
```bash
curl -X PATCH "http://localhost:8080/api/leaves/$LEAVE_ID/approve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"reviewedById\":$MGR_ID}"
```
**Expected: 200** — `"status":"APPROVED"`, `"reviewedByName":"Bob Boss"`

### T12 — Balance Updates After Approve (usedDays increases)
```bash
curl "http://localhost:8080/api/leaves/balances/$EMP_ID?year=2026" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — ANNUAL: `pendingDays:0`, `usedDays:5`, `remainingDays:15`

### T13 — Approve Already-Approved Request (409)
```bash
curl -X PATCH "http://localhost:8080/api/leaves/$LEAVE_ID/approve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"reviewedById\":$MGR_ID}"
```
**Expected: 409** — `{"message":"Only PENDING requests can be approved"}`

### T14 — Reject a Leave (200)
```bash
# Apply new leave first
NEW_LEAVE_ID=$(curl -s -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"SICK\",\"startDate\":\"2026-05-04\",\"endDate\":\"2026-05-05\"}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

curl -X PATCH "http://localhost:8080/api/leaves/$NEW_LEAVE_ID/reject" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"reviewedById\":$MGR_ID,\"rejectionReason\":\"Team is short-staffed\"}"
```
**Expected: 200** — `"status":"REJECTED"`, `"rejectionReason":"Team is short-staffed"`

### T15 — Approve/Reject as EMPLOYEE (403)
```bash
curl -X PATCH "http://localhost:8080/api/leaves/1/approve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"reviewedById\":$MGR_ID}"
```
**Expected: 403**

---

## 4. Cancel Leave

### T16 — Cancel PENDING Leave (200)
```bash
# Apply new leave
CANCEL_ID=$(curl -s -X POST http://localhost:8080/api/leaves/apply \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -d "{\"employeeId\":$EMP_ID,\"leaveType\":\"CASUAL\",\"startDate\":\"2026-08-03\",\"endDate\":\"2026-08-03\"}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

curl -X PATCH "http://localhost:8080/api/leaves/$CANCEL_ID/cancel?employeeId=$EMP_ID" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** — `"status":"CANCELLED"`, pendingDays restored

### T17 — Cancel Already-Cancelled Request (409)
```bash
curl -X PATCH "http://localhost:8080/api/leaves/$CANCEL_ID/cancel?employeeId=$EMP_ID" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 409** — `{"message":"Request is already cancelled"}`

### T18 — Cancel Another Employee's Request (400)
```bash
curl -X PATCH "http://localhost:8080/api/leaves/$LEAVE_ID/cancel?employeeId=9999" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 400** — `{"message":"You can only cancel your own leave requests"}`

---

## 5. Read Endpoints

### T19 — Get All Leaves as ADMIN (200)
```bash
curl "http://localhost:8080/api/leaves" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Paged response with all leave requests

### T20 — Filter by Status (200)
```bash
curl "http://localhost:8080/api/leaves?status=PENDING" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Only PENDING requests

### T21 — Get All Leaves as EMPLOYEE (403)
```bash
curl "http://localhost:8080/api/leaves" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 403**

### T22 — Get Leaves for Employee (200)
```bash
curl "http://localhost:8080/api/leaves/employee/$EMP_ID" \
  -H "Authorization: Bearer $EMP_TOKEN"
```
**Expected: 200** — All leave requests for that employee

### T23 — Get Leave by ID (200)
```bash
curl "http://localhost:8080/api/leaves/$LEAVE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 200** — Full leave request details

### T24 — Get Leave Not Found (404)
```bash
curl "http://localhost:8080/api/leaves/9999" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected: 404**

---

## 6. Permission Matrix

| Endpoint | EMPLOYEE | MANAGER | ADMIN |
|---|---|---|---|
| POST /api/leaves/apply | ✅ 201 | ✅ 201 | ✅ 201 |
| PATCH /api/leaves/{id}/approve | ❌ 403 | ✅ 200 | ✅ 200 |
| PATCH /api/leaves/{id}/reject | ❌ 403 | ✅ 200 | ✅ 200 |
| PATCH /api/leaves/{id}/cancel | ✅ 200 (own) | ✅ 200 (own) | ✅ 200 (own) |
| GET /api/leaves | ❌ 403 | ✅ 200 | ✅ 200 |
| GET /api/leaves/{id} | ✅ 200 | ✅ 200 | ✅ 200 |
| GET /api/leaves/employee/{id} | ✅ 200 | ✅ 200 | ✅ 200 |
| POST /api/leaves/balances | ❌ 403 | ❌ 403 | ✅ 201 |
| GET /api/leaves/balances/{id} | ✅ 200 | ✅ 200 | ✅ 200 |

---

## 7. Full Flow Test

| Step | Action | Expected |
|------|--------|----------|
| 1 | POST /api/leaves/balances (ANNUAL, 20 days) | 201 |
| 2 | POST /api/leaves/balances (SICK, 10 days) | 201 |
| 3 | GET /api/leaves/balances/{empId} | 200, 2 items |
| 4 | POST /api/leaves/apply (ANNUAL, 5 days) | 201, PENDING |
| 5 | GET /api/leaves/balances/{empId} | ANNUAL pendingDays=5, remaining=15 |
| 6 | PATCH /api/leaves/{id}/approve | 200, APPROVED |
| 7 | GET /api/leaves/balances/{empId} | ANNUAL usedDays=5, pendingDays=0, remaining=15 |
| 8 | PATCH /api/leaves/{id}/approve again | 409 |
| 9 | POST /api/leaves/apply (SICK, 3 days) | 201, PENDING |
| 10 | PATCH /api/leaves/{id}/reject | 200, REJECTED, pendingDays restored |
| 11 | POST /api/leaves/apply (CASUAL, no balance) | 422 |
| 12 | POST /api/leaves/apply (UNPAID, 2 days) | 201, no balance needed |
| 13 | GET /api/leaves?status=PENDING | 200 |
| 14 | PATCH cancel (own) | 200, CANCELLED |

---

*Version 4 — Leave Management added. All V1+V2+V3 endpoints still work.*
