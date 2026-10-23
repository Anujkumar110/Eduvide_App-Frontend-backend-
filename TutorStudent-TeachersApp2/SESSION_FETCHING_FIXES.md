# Session Fetching Fixes

## Problems Fixed

### Issue 1: Sessions disappear when going back from live session
**Problem:** When a teacher ended a live session and navigated back to Sessions tab, the session would disappear instead of showing in the "Completed" tab.

**Root Cause:**
- `updateStatusOnBackend()` is async but `onDispose` doesn't wait for it
- Session list wasn't refreshed after status update
- Local state was out of sync with backend

**Fix (Liveclassscreen.kt):**
```kotlin
DisposableEffect(Unit) {
    onDispose {
        if (isEndedByTeacher) {
            // ✅ Update status IMMEDIATELY in local state first
            viewModel.updateStatus(roomId, ClassStatus.COMPLETED)
            // Then update on backend (async, doesn't block)
            viewModel.updateStatusOnBackend(roomId, "completed")
            // ✅ Force reload sessions to sync with backend
            viewModel.loadTeacherSessions()
        }
        webRTCClient.disconnect()
        signalingClient.disconnect()
        eglBase.release()
    }
}
```

### Issue 2: Old sessions stuck in "Ongoing" status
**Problem:** Sessions that were old/completed were incorrectly showing as "Ongoing" in the teacher's sessions list.

**Root Cause:**
- Sessions manually set to "ongoing" (by clicking "Join") could get stuck if:
  - Teacher disconnected without properly ending the session
  - WebRTC connection failed to establish
  - Cron job missed the cleanup cycle
- No mechanism to detect and cleanup "stuck" ongoing sessions

**Fix (Backend - class.service.ts):**
Enhanced `autoCompleteExpiredSessions()` to detect and fix stuck sessions:

```typescript
// ✅ FIX: Handle STUCK ongoing sessions
else if (now < endTime) {
  const connectedPeers = roomManager.getConnectedPeers(cls._id.toString());
  const hasNoActiveConnection = connectedPeers.length === 0;

  // If session is marked "ongoing" but has NO active peers for more than 2 minutes
  if (hasNoActiveConnection) {
    const updatedAt = new Date(cls.updatedAt || cls.createdAt);
    const minutesSinceUpdate = (now.getTime() - updatedAt.getTime()) / (1000 * 60);

    if (minutesSinceUpdate > 2) {
      // Stuck in ongoing - revert to published (scheduled)
      await Class.findByIdAndUpdate(cls._id, { status: 'published' });
      console.log(`[AutoComplete] Class ${cls._id} marked as PUBLISHED (stuck in ongoing)`);
      stuckCount++;
    }
  }
}
```

**Additional Features:**
1. Added manual cleanup endpoint for teachers: `POST /api/v1/classes/cleanup-stuck-sessions`
2. Backend cron job runs every 1 minute to auto-cleanup stuck sessions

## Files Modified

### Android App
1. **Liveclassscreen.kt** - Added session status sync on dispose
2. **SessionsTab.kt** - Improved reload logic when tab becomes visible
3. **Sessionviewmodel.kt** - Removed infinite loop risk
4. **ApiService.kt** - Added `cleanupStuckSessions()` endpoint

### Backend
1. **class.service.ts** - Enhanced `autoCompleteExpiredSessions()` to handle stuck sessions
2. **class.controller.ts** - Added `cleanupStuckSessions()` controller
3. **class.routes.ts** - Registered cleanup endpoint

## How It Works Now

### Session Lifecycle Flow:
```
Teacher creates session → published
Teacher clicks "Join" → ongoing (manual update via API)
During live session → ongoing
Teacher ends session → completed (local + backend sync)
Navigate back → Session list refreshed, shows in "Completed" tab
```

### Stuck Session Detection:
```
Every 1 minute (cron job):
1. Check all sessions with status = "ongoing"
2. If session time has ended:
   - Has students → mark as "completed"
   - No students → mark as "cancelled" (missed)
3. If session time hasn't ended but NO active WebRTC peers for 2+ minutes:
   - Mark as "published" (revert to scheduled)
```

## Testing Steps

1. **Test Session End Sync:**
   - Create a session
   - Click "Join" to start live session
   - End the session
   - Navigate back to Sessions tab
   - ✅ Session should appear in "Completed" tab

2. **Test Stuck Session Cleanup:**
   - Manually set a session status to "ongoing" in database (without starting WebRTC)
   - Wait 1-2 minutes
   - Refresh sessions list
   - ✅ Session should revert to "Scheduled" or be cleaned up by cron

3. **Test Manual Cleanup Endpoint:**
   - Call `POST /api/v1/classes/cleanup-stuck-sessions` with teacher token
   - ✅ Should return count of cleaned sessions

## API Endpoint

### Cleanup Stuck Sessions
```
POST /api/v1/classes/cleanup-stuck-sessions
Authorization: Bearer <teacher_token>

Response:
{
  "success": true,
  "message": "Cleaned up: 2 unstuck, 1 completed, 0 missed",
  "data": {
    "completed": 1,
    "missed": 0,
    "unstuck": 2
  }
}
```

## Notes
- Backend cron job runs every 1 minute automatically
- Manual endpoint available for immediate cleanup if needed
- All changes are backward compatible
