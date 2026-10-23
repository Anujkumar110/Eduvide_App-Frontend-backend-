# Ongoing Sessions Display Fix

## Problem
Purani sessions jo backend pe `status: "ongoing"` hain, woh "Ongoing" tab mein nahi dikh rahi thin.

## Changes Made

### 1. Enhanced Debug Logging (Sessionviewmodel.kt)
Added detailed logging to track exactly what's coming from backend:

```kotlin
// Raw backend data
Log.d("SessionVM", "📊 Raw backend data:")
classes.forEachIndexed { index, c ->
    Log.d("SessionVM", "  [$index] ID: ${c.id} | Title: ${c.title} | Status: '${c.status}'")
}

// Mapped sessions
Log.d("SessionVM", "📊 Mapped sessions:")
_sessions.value.forEachIndexed { index, s ->
    Log.d("SessionVM", "  [$index] ID: ${s.roomId} | Title: ${s.studentName} | Status: ${s.status}")
}

// Status breakdown
val statusCounts = classes.groupBy { it.status }.mapValues { it.value.size }
Log.d("SessionVM", "📊 Status breakdown: $statusCounts")
```

### 2. Enhanced Debug Logging (SessionsTab.kt)
Added logging for filtered sessions per tab:

```kotlin
android.util.Log.d("SessionsTab", "📊 Tab $selectedTab (${tabs[selectedTab]}): ${filtered.size} of ${allSessions.size} sessions")
filtered.forEachIndexed { index, session ->
    android.util.Log.d("SessionsTab", "  [$index] ${session.studentName} | Status: ${session.status}")
}
```

### 3. Tab Count Badges (SessionsTab.kt)
Added visual count badges on each tab to show how many sessions are in each category:

```kotlin
// Calculate counts
val tabCounts = remember(allSessions) {
    listOf(
        allSessions.count { it.status == ClassStatus.SCHEDULED },
        allSessions.count { it.status == ClassStatus.ONGOING },
        allSessions.count { it.status == ClassStatus.COMPLETED },
        allSessions.count { it.status == ClassStatus.MISSED }
    )
}

// Display in tabs
Tab(
    text = {
        Row {
            Text(title)
            if (tabCounts[index] > 0) {
                Text(
                    text = "${tabCounts[index]}",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
)
```

## How to Test

### Step 1: Check Backend Data
Run the app and check the logs in Logcat:

```bash
adb logcat | grep -E "SessionVM|SessionsTab"
```

You should see:
```
SessionVM: 📊 Raw backend data:
SessionVM:   [0] ID: xxx | Title: yyy | Status: 'ongoing'
SessionVM:   [1] ID: aaa | Title: bbb | Status: 'published'
SessionVM: 📊 Status breakdown: {ongoing=2, published=3, completed=1}
```

### Step 2: Check Filtering Logs
```
SessionsTab: 📊 Tab 1 (Ongoing): 2 of 6 sessions
SessionsTab:   [0] Session Name | Status: ONGOING
SessionsTab:   [1] Another Session | Status: ONGOING
```

### Step 3: Verify UI
- Open Sessions tab
- You should see badges on tabs showing counts like: `Scheduled (3)`, `Ongoing (2)`, etc.
- Click on "Ongoing" tab
- All sessions with backend status "ongoing" should appear

## Expected Behavior

### If sessions ARE coming from backend:
✅ Logs will show `Status: 'ongoing'` in raw data
✅ Logs will show `Status: ONGOING` in mapped sessions
✅ Tab badge will show count (e.g., "Ongoing (2)")
✅ Sessions will display in Ongoing tab

### If sessions are NOT coming from backend:
❌ Logs will show empty or different status
❌ Backend might be returning wrong status
❌ Need to check database directly

## Database Check (if needed)

If logs show no ongoing sessions, check MongoDB directly:

```javascript
// Check all classes with ongoing status
db.classes.find({ status: "ongoing" }).pretty()

// Check all classes for a specific teacher
db.classes.find({ teacherId: ObjectId("YOUR_TEACHER_ID") }).pretty()
```

## Backend Cron Auto-Cleanup

The backend now runs a cron job every 1 minute that:
1. Checks all `ongoing` sessions
2. If session time has ended → marks as `completed` or `cancelled`
3. If session has NO active WebRTC peers for 2+ minutes → reverts to `published`

This means old stuck `ongoing` sessions might get auto-cleaned. Check logs to confirm.

## Files Modified
1. `app/src/main/java/com/example/tutorapp/viewmodel/Sessionviewmodel.kt`
2. `app/src/main/java/com/example/tutorapp/teacher/SessionsTab.kt`
