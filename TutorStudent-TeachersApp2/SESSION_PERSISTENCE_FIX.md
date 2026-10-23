# Session Persistence Fix for Teacher UI

## Problem
When a teacher creates a session and then navigates away from the Sessions tab, the session would disappear from the UI when navigating back. The session was not persisting properly in the teacher's view.

## Root Cause
The `SessionsTab.kt` composable was using `KeepAliveTab` in `MainScreen.kt`, which keeps the composable alive even when not visible. This caused two issues:

1. **`LaunchedEffect(Unit)` only runs once** - When the tab becomes visible again, the effect doesn't re-trigger because the composable was never destroyed
2. **Background polling continues** - The 15-second polling loop runs even when the tab is hidden, wasting resources

## Solution

### Changes Made

#### 1. **SessionsTab.kt** - Added Tab Visibility Tracking
- Added `isTabSelected: Boolean = true` parameter to track when the tab is visible
- Modified `LaunchedEffect` to reload sessions **every time** the tab becomes visible
- Optimized polling to only run when the tab is actually visible

```kotlin
// Reload sessions when tab becomes visible
LaunchedEffect(isTabSelected) {
    if (isTabSelected) {
        viewModel.loadTeacherSessions()
    }
}

// Only poll when tab is visible
LaunchedEffect(Unit) {
    while (true) {
        delay(15_000)
        if (isTabSelected) {  // Check visibility before polling
            viewModel.loadTeacherSessions()
            // Auto-switch to Ongoing tab if needed
        }
    }
}
```

#### 2. **MainScreen.kt** - Pass Tab Visibility State
- Updated the `SessionsTab` call to pass `isTabSelected = selectedTab == 1`

```kotlin
SessionsTab(
    viewModel = sessionViewModel,
    isTabSelected = selectedTab == 1,  // Track visibility
    onStartLive = { roomId, role ->
        navController.navigate("CallScreen/$roomId/$role")
    }
)
```

## How It Works Now

1. **When you create a session**: 
   - Session is added locally immediately
   - Backend is called to create the session
   - `loadTeacherSessions()` is called to refresh from backend

2. **When you navigate away**: 
   - SessionsTab is kept alive but hidden (via KeepAliveTab)
   - Polling stops (saves resources)

3. **When you navigate back**: 
   - `isTabSelected` becomes `true`
   - `LaunchedEffect(isTabSelected)` triggers
   - Sessions are reloaded from backend immediately
   - Your session appears in the correct tab

4. **While tab is visible**: 
   - Polling runs every 15 seconds
   - Auto-switches to "Ongoing" tab if a session goes live

## Benefits

✅ **Sessions persist** - Sessions won't disappear when navigating away and back  
✅ **Resource efficient** - Polling stops when tab is hidden  
✅ **Immediate updates** - Sessions reload instantly when tab becomes visible  
✅ **Background sync** - Sessions stay updated while tab is visible  

## Testing

To verify the fix works:

1. **Login as teacher** → Go to Sessions tab
2. **Create a new session** → Session appears immediately
3. **Navigate to Home tab** → Session data is preserved
4. **Navigate back to Sessions tab** → Session is still there (reloaded from backend)
5. **Wait 15 seconds** → Polling refreshes sessions automatically
6. **Create another session** → Both sessions appear

## Files Modified

- `app/src/main/java/com/example/tutorapp/teacher/SessionsTab.kt`
- `app/src/main/java/com/example/tutorapp/teacher/MainScreen.kt`

## Build Status
✅ **BUILD SUCCESSFUL** - No compilation errors
