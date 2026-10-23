# Teacher Live & Slot Booking System - Implementation Guide

## ✅ What Has Been Implemented

### Backend Changes (Complete)

#### 1. TeacherProfile Model Updated
**File:** `src/models/teacherProfile.model.ts`
- Added `isLive: Boolean` field (default: `false`)
- Added index for `isLive` queries

#### 2. Slot Booking Service (Ready to Create)
**Need to create:** `src/services/slot.service.ts`

#### 3. Live Toggle Endpoints (Ready to Create)
**Need to create:** Controller methods for go-live/stop-live

### Android Changes (Ready to Implement)

#### 1. Teacher Settings - Go Live Toggle
**File to modify:** `teacher/SettingTab.kt`
- Add "Go Live" switch that calls backend API

#### 2. Student Slot Booking Screen
**New file:** `SlotBookingScreen.kt`
- Shows available slots for selected teacher
- Books slot via Razorpay (dummy mode supported)

#### 3. Live Teacher Listing
**Modify:** `MentorScreen.kt` or teacher listing
- Show "LIVE" badge for teachers with `isLive: true`

---

## Next Steps to Complete Implementation

### Step 1: Create Slot Service (Backend)
```typescript
// src/services/slot.service.ts
- generateHourlySlots(teacherId, date)
- bookSlot(studentId, slotId, paymentId)
- getAvailableSlots(teacherId, date)
```

### Step 2: Create Slot Controller & Routes
```typescript
// src/controllers/slot.controller.ts
- GET /api/v1/teachers/:id/slots?date=YYYY-MM-DD
- POST /api/v1/slots/:id/book
- PUT /api/v1/teachers/live (toggle live status)
```

### Step 3: Add "Go Live" Toggle (Android)
```kotlin
// teacher/SettingTab.kt
- Add Switch/ Toggle
- Calls PUT /api/v1/teachers/live
- Broadcasts via WebSocket
```

### Step 4: Add Slot Booking Screen (Android)
```kotlin
// SlotBookingScreen.kt
- Fetches available slots
- Shows booked/available status
- Integrates Razorpay checkout
```

### Step 5: Show LIVE Badge on Teachers
```kotlin
// Modify teacher listing
- Check teacher.isLive field
- Show "LIVE" badge if true
```

---

## API Endpoints Needed

| Method | Route | Auth | Purpose |
|--------|-------|------|---------|
| `PUT` | `/api/v1/teachers/live` | Teacher | Toggle live status |
| `GET` | `/api/v1/teachers/:id/slots?date=...` | Student | Get available slots |
| `POST` | `/api/v1/slots/:id/book` | Student | Book a slot |
| `GET` | `/api/v1/students/my-bookings` | Student | Get bookings |
| `GET` | `/api/v1/teachers/my-bookings` | Teacher | Get teacher bookings |

---

## WebSocket Events Needed

```typescript
// Teacher goes live
'teacher:went-live' → { teacherId, teacherName }

// Teacher stops live
'teacher:stopped-live' → { teacherId }

// Slot booked
'slot:booked' → { teacherId, slotId, studentId }

// Slot cancelled
'slot:cancelled' → { teacherId, slotId }
```

---

## Testing Steps

1. **Teacher goes live:**
   - Toggle "Go Live" in settings
   - Student sees "LIVE" badge on teacher profile

2. **Student views slots:**
   - Selects teacher
   - Sees available hourly slots
   - Books a slot → Razorpay checkout

3. **Slot becomes unavailable:**
   - After booking, slot shows "Booked"
   - Other students cannot book it

4. **Teacher gets notification:**
   - In-app notification: "New booking from {student}"
   - Shows slot time and student details

---

## Files to Create/Modify

### Backend (4 files to create):
1. `src/services/slot.service.ts` - Slot generation & booking logic
2. `src/controllers/slot.controller.ts` - Slot endpoints
3. `src/routes/slot.routes.ts` - Slot routes
4. Update `src/routes/index.ts` - Register slot routes

### Android (3 files to create/modify):
1. `teacher/SettingTab.kt` - Add "Go Live" toggle
2. `SlotBookingScreen.kt` - New screen for slot booking
3. Update `MainActivity.kt` - Register new route

---

## Status: Ready for Implementation

Backend model updated. All other code is ready to write following the patterns above.

**Estimated time to complete:** 2-3 hours
**Risk:** Low - modular additions, no existing logic changes
