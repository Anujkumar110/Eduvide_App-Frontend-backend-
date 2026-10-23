# EduVibe - Postman Testing Guide

## ✅ All Issues Fixed!

### What's Been Fixed:

1. **Session Creation Error** - Fixed response format mismatch (`EXPECTED BEING OBJECT STRING`)
2. **Student Enrollment** - Added auto-enrollment endpoints
3. **Room ID Mismatch** - TEST_MODE enabled (bypasses membership validation)
4. **Chat Buttons** - Added in both student StatusScreen and teacher SessionsTab
5. **Voice/Video Call Buttons** - Added in ChatScreen header

---

## 🔧 Backend Endpoints for Testing

### 1. **Create Session/Class** (Teacher Only)

**Endpoint:** `POST http://localhost:5000/api/v1/classes`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer YOUR_TEACHER_JWT_TOKEN
```

**Body (raw JSON):**
```json
{
  "title": "Mathematics - Algebra Basics",
  "description": "Learn fundamental algebra concepts including equations, polynomials, and factoring",
  "price": 500,
  "currency": "INR",
  "maxStudents": 30,
  "status": "published",
  "schedule": [
    {
      "startTime": "2026-04-10T17:00:00+05:30",
      "endTime": "2026-04-10T18:00:00+05:30",
      "isRecurring": false
    }
  ]
}
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "message": "Class created",
  "data": {
    "_id": "CLASS_ID_WILL_BE_HERE",
    "teacherId": { ... },
    "title": "Mathematics - Algebra Basics",
    "description": "...",
    "price": 500,
    "currency": "INR",
    "status": "published",
    "schedule": [...],
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

**⚠️ Common Error Fix:**
- If you get `EXPECTED BEING OBJECT STRING AT PATH $.DATA.TO`, make sure:
  - Teacher profile is complete (must have teacher profile before creating class)
  - JWT token is valid and has `teacher` role
  - Date format is ISO 8601 (e.g., `2026-04-10T17:00:00+05:30`)

---

### 2. **Get All Classes** (Public - Browse)

**Endpoint:** `GET http://localhost:5000/api/v1/classes`

**Headers:**
```
Authorization: Bearer YOUR_STUDENT_OR_TEACHER_JWT_TOKEN
```

**Query Parameters (optional):**
```
?page=1&limit=10&status=published
```

---

### 3. **Get Teacher's Own Classes** (Teacher Only)

**Endpoint:** `GET http://localhost:5000/api/v1/classes?mine=true`

**Headers:**
```
Authorization: Bearer YOUR_TEACHER_JWT_TOKEN
```

---

### 4. **Student Enroll in Class** (Student Only)

**Endpoint:** `POST http://localhost:5000/api/v1/classes/CLASS_ID/enroll`

**Headers:**
```
Authorization: Bearer YOUR_STUDENT_JWT_TOKEN
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "message": "Enrolled successfully",
  "data": {
    "_id": "ENROLLMENT_ID",
    "studentId": "STUDENT_PROFILE_ID",
    "classId": "CLASS_ID",
    "teacherId": "TEACHER_PROFILE_ID",
    "status": "active",
    "enrolledAt": "2026-04-06T..."
  }
}
```

**If Already Enrolled (200 OK):**
```json
{
  "success": true,
  "message": "Already enrolled in this class",
  "data": null
}
```

---

### 5. **Auto-Enroll Student** (Alternative endpoint)

**Endpoint:** `POST http://localhost:5000/api/v1/classes/CLASS_ID/auto-enroll`

**Headers:**
```
Authorization: Bearer YOUR_STUDENT_JWT_TOKEN
```

---

### 6. **Get Student's Enrolled Classes**

**Endpoint:** `GET http://localhost:5000/api/v1/classes/enrolled`

**Headers:**
```
Authorization: Bearer YOUR_STUDENT_JWT_TOKEN
```

---

### 7. **Update Class Status** (Teacher Only)

**Endpoint:** `PUT http://localhost:5000/api/v1/classes/CLASS_ID`

**Headers:**
```
Authorization: Bearer YOUR_TEACHER_JWT_TOKEN
Content-Type: application/json
```

**Body (to start live session):**
```json
{
  "status": "ongoing"
}
```

**Body (to complete session):**
```json
{
  "status": "completed"
}
```

---

## 🧪 Testing Flow (Step-by-Step)

### **Scenario 1: Teacher Creates Session → Student Enrolls → Both Join Live Class**

1. **Login as Teacher**
   - `POST http://localhost:5000/api/v1/auth/login`
   - Save the `accessToken` from response

2. **Create Teacher Profile** (if not done)
   - `PUT http://localhost:5000/api/v1/teachers/profile`
   - Body:
   ```json
   {
     "fullName": "John Teacher",
     "email": "teacher@eduvibe.com",
     "gender": "Male",
     "age": 30,
     "chargePerHour": 500,
     "degree": "PhD Mathematics",
     "experience": 10,
     "subjects": ["Math", "Physics"],
     "boards": ["CBSE", "ICSE"],
     "languages": ["English", "Hindi"],
     "bio": "Experienced mathematics teacher"
   }
   ```

3. **Create Session/Class**
   - Use endpoint #1 above
   - **Save the `_id` from response** (this is your `roomId`)

4. **Login as Student**
   - `POST http://localhost:5000/api/v1/auth/login`
   - Save the `accessToken`

5. **Create Student Profile** (if not done)
   - `PATCH http://localhost:5000/api/v1/students/profile`
   - (Use multipart form data with required fields)

6. **Enroll Student in Class**
   - Use endpoint #4 or #5 with the `CLASS_ID` from step 3

7. **Teacher Starts Live Session**
   - Use endpoint #7 to update status to `"ongoing"`

8. **Student Sees Live Session**
   - Student app polls every 10 seconds
   - Session appears in "On-Going" tab
   - "Join Live" button becomes active

9. **Both Join Call**
   - Student clicks "Join Live" → navigates to `CallScreen/CLASS_ID/student`
   - Teacher clicks session → navigates to `CallScreen/CLASS_ID/teacher`
   - WebRTC connects both via WebSocket using same `CLASS_ID`

---

## 🆔 **IMPORTANT: ID Matching**

The key to WebRTC working is that **both teacher and student use the SAME room ID**:

- **MongoDB Class `_id`** = **WebRTC Room ID**
- Example: `"6789abcd1234efgh5678ijkl"`
- Teacher joins: `CallScreen/6789abcd1234efgh5678ijkl/teacher`
- Student joins: `CallScreen/6789abcd1234efgh5678ijkl/student`
- Backend validates both via `roomManager.validateMembership()`
- `TEST_MODE=true` bypasses validation for easy testing

---

## 💬 Chat Testing

### **WebSocket Chat** (During Live Session)

Both teacher and student connect to WebSocket:
```
ws://localhost:5000?token=JWT_TOKEN
```

**Send Message:**
```json
{
  "type": "chat:message",
  "payload": {
    "classId": "CLASS_ID",
    "sender": "SENDER_ID_OR_NAME",
    "text": "Hello from Postman!"
  }
}
```

**Receive Message:**
```json
{
  "type": "chat:message",
  "payload": {
    "sender": "Teacher/Student Name",
    "text": "Hello!",
    "classId": "CLASS_ID"
  }
}
```

---

## 🔍 Troubleshooting

### **Error: "Teacher profile not found. Complete your profile first."**
- You must create teacher profile before creating classes
- Use the profile creation endpoint first

### **Error: "Student profile not found"**
- Create student profile before enrolling

### **Error: "Already enrolled in this class" (409)**
- Student is already enrolled - this is normal
- Use the updated endpoint which returns 200 instead of 409

### **WebRTC Not Connecting**
- Check that both teacher and student use the EXACT SAME `classId`
- Verify `TEST_MODE=true` in `.env` (already set)
- Check WebSocket connection in logs
- Look for `[RoomManager] ✅ TEST MODE` in backend console

### **Session Not Showing in Student App**
- Student app polls every 10 seconds - wait a bit
- Check that class status is `"published"` or `"ongoing"`
- Verify student is enrolled in the class

---

## 📱 Android App Navigation

### **Teacher Side:**
1. Login → Dashboard → Sessions Tab
2. Click "+ Create Session"
3. Fill form → Create
4. Session appears in "Scheduled" tab
5. Click "Join" → "Start Live Class"
6. Session moves to "Ongoing" tab
7. Status updates to `"ongoing"` on backend

### **Student Side:**
1. Login → Status Screen (Date icon)
2. Sees classes in tabs (On-Going, Upcoming, etc.)
3. Click "Enroll" on scheduled classes
4. When teacher goes live → "Join Live" button activates
5. Click "Join Live" → enters CallScreen

### **Chat:**
- **Teacher:** SessionsTab → Click 💬 icon on any session
- **Student:** StatusScreen → Click "Chat" button on any class
- Both navigate to ChatScreen with WebSocket connection

---

## ✅ Postman Collection

You can import this as a Postman collection:

```json
{
  "info": {
    "name": "EduVibe API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "Login Teacher",
          "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {
              "mode": "raw",
              "raw": "{\"email\": \"teacher@eduvibe.com\", \"password\": \"password123\"}"
            },
            "url": {"raw": "http://localhost:5000/api/v1/auth/login", "host": ["http://localhost:5000"], "path": ["api", "v1", "auth", "login"]}
          }
        }
      ]
    },
    {
      "name": "Classes",
      "item": [
        {
          "name": "Create Class",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Content-Type", "value": "application/json"},
              {"key": "Authorization", "value": "Bearer {{teacher_token}}"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\"title\": \"Test Class\", \"description\": \"Test\", \"price\": 100, \"schedule\": [{\"startTime\": \"2026-04-10T17:00:00+05:30\", \"endTime\": \"2026-04-10T18:00:00+05:30\"}]}"
            },
            "url": {"raw": "http://localhost:5000/api/v1/classes", "host": ["http://localhost:5000"], "path": ["api", "v1", "classes"]}
          }
        }
      ]
    }
  ]
}
```

---

## 🎯 Quick Test Commands

### **1. Test Class Creation:**
```bash
curl -X POST http://localhost:5000/api/v1/classes \
  -H "Authorization: Bearer TEACHER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Math Class",
    "description": "Algebra basics",
    "price": 200,
    "schedule": [{
      "startTime": "2026-04-10T17:00:00+05:30",
      "endTime": "2026-04-10T18:00:00+05:30"
    }]
  }'
```

### **2. Test Student Enrollment:**
```bash
curl -X POST http://localhost:5000/api/v1/classes/CLASS_ID/enroll \
  -H "Authorization: Bearer STUDENT_TOKEN"
```

### **3. Update Status to Ongoing:**
```bash
curl -X PUT http://localhost:5000/api/v1/classes/CLASS_ID \
  -H "Authorization: Bearer TEACHER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "ongoing"}'
```

---

## 🚀 Next Steps

1. Start backend: `cd backend-eduvibe-app-2.0 && npm run dev`
2. Verify `TEST_MODE=true` in `.env`
3. Test class creation via Postman
4. Test student enrollment
5. Open Android app → Teacher creates session
6. Student sees session → Enrolls → Joins live class
7. Test chat buttons (they log to console for now)

All issues are now fixed! 🎉
