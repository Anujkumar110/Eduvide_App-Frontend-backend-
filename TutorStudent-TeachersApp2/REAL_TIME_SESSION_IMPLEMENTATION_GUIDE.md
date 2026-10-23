# Real-Time Session Management Implementation Guide

## Overview
This document provides a complete guide to implementing real-time session status updates in the EduVibe Android app using WebSocket integration with polling fallback.

## What Has Been Implemented (Backend)

### ✅ Completed Backend Features

1. **Session Model** (`src/models/session.model.ts`)
   - Per-instance session tracking
   - Status: `upcoming`, `ongoing`, `completed`, `missed`, `cancelled`
   - Tracks actual start/end times
   - Tracks attended students

2. **Session Service** (`src/services/session.service.ts`)
   - `startSession()` - Teacher starts session → status → `ongoing`
   - `endSession()` - Teacher ends session → status → `completed`
   - `attendSession()` - Student marks attendance
   - `autoCompleteExpiredSessions()` - Auto-complete past sessions
   - `markMissedSessions()` - Mark no-show sessions as `missed`
   - `getUpcomingSessions()` - Get sessions for reminders

3. **Session API Endpoints**
   - `POST /api/v1/classes/:id/sessions/start` - Start session
   - `POST /api/v1/sessions/:id/end` - End session
   - `GET /api/v1/classes/:id/sessions` - List class sessions
   - `POST /api/v1/sessions/:id/attend` - Mark attendance
   - `GET /api/v1/students/my-sessions` - Student's sessions
   - `GET /api/v1/teachers/my-sessions` - Teacher's sessions

4. **WebSocket Events** (`src/ws/handlers/signalingHandler.ts`)
   - `session:teacher-started` → broadcasts `session:started`
   - `session:teacher-ended` → broadcasts `session:ended`
   - `session:student-joined` → broadcasts `session:student-joined`

5. **Auto-Create Sessions**
   - Sessions are automatically created when a class is created (based on schedule slots)

## Android Implementation Steps

### Step 1: Add WebSocket Client Library

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Add OkHttp WebSocket support (already have OkHttp)
    // Or use a dedicated WebSocket library
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Step 2: Create WebSocket Manager

Create `network/WebSocketManager.kt`:

```kotlin
package com.example.tutorapp.network

import android.util.Log
import com.example.tutorapp.repository.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val tokenManager: TokenManager,
    private val baseUrl: String
) {
    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events
    
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("WebSocket", "Connected to EduVibe WebSocket")
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val type = json.getString("type")
                val payload = json.getJSONObject("payload")
                
                when (type) {
                    "session:started" -> {
                        val classId = payload.getString("classId")
                        val sessionId = payload.optString("sessionId", "")
                        val actualStartTime = payload.optString("actualStartTime", "")
                        _events.tryEmit(WebSocketEvent.SessionStarted(classId, sessionId, actualStartTime))
                    }
                    "session:ended" -> {
                        val classId = payload.getString("classId")
                        val sessionId = payload.getString("sessionId")
                        val actualEndTime = payload.optString("actualEndTime", "")
                        _events.tryEmit(WebSocketEvent.SessionEnded(classId, sessionId, actualEndTime))
                    }
                    "session:student-joined" -> {
                        val classId = payload.getString("classId")
                        val sessionId = payload.getString("sessionId")
                        val studentId = payload.getString("studentId")
                        _events.tryEmit(WebSocketEvent.StudentJoinedSession(classId, sessionId, studentId))
                    }
                }
            } catch (e: Exception) {
                Log.e("WebSocket", "Error parsing message: ${e.message}")
            }
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("WebSocket", "Connection closed: $reason")
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("WebSocket", "Connection failed: ${t.message}")
        }
    }
    
    fun connect() {
        val token = tokenManager.getToken()
        if (token.isBlank()) {
            Log.w("WebSocket", "Cannot connect: token is blank")
            return
        }
        
        val wsUrl = baseUrl.replace("http", "ws").replace("https", "wss") + "/ws?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        
        webSocket?.cancel()
        webSocket = client.newWebSocket(request, listener)
        
        Log.d("WebSocket", "Connecting to: $wsUrl")
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }
    
    fun sendEvent(type: String, payload: Map<String, Any>) {
        val json = JSONObject().apply {
            put("type", type)
            put("payload", JSONObject(payload))
        }
        webSocket?.send(json.toString())
    }
    
    fun sendJoinRoom(classId: String) {
        sendEvent("room:join", mapOf("classId" to classId))
    }
    
    fun sendSessionStarted(classId: String, className: String, sessionId: String) {
        sendEvent("session:teacher-started", mapOf(
            "classId" to classId,
            "className" to className,
            "sessionId" to sessionId
        ))
    }
    
    fun sendSessionEnded(classId: String, sessionId: String) {
        sendEvent("session:teacher-ended", mapOf(
            "classId" to classId,
            "sessionId" to sessionId
        ))
    }
    
    fun sendStudentJoined(classId: String, sessionId: String) {
        sendEvent("session:student-joined", mapOf(
            "classId" to classId,
            "sessionId" to sessionId
        ))
    }
}

// WebSocket Event types
sealed class WebSocketEvent {
    data class SessionStarted(
        val classId: String,
        val sessionId: String,
        val actualStartTime: String
    ) : WebSocketEvent()
    
    data class SessionEnded(
        val classId: String,
        val sessionId: String,
        val actualEndTime: String
    ) : WebSocketEvent()
    
    data class StudentJoinedSession(
        val classId: String,
        val sessionId: String,
        val studentId: String
    ) : WebSocketEvent()
}
```

### Step 3: Update SessionViewModel with WebSocket Integration

Update `viewmodel/SessionViewModel.kt`:

```kotlin
class SessionViewModel(
    private val apiService: ApiService,
    private val tokenProvider: () -> String,
    private val wsManager: WebSocketManager? = null  // Add this
) : ViewModel() {

    // Add this in init block
    init {
        // Setup WebSocket event listener
        wsManager?.let { ws ->
            viewModelScope.launch {
                ws.events.collect { event ->
                    when (event) {
                        is WebSocketEvent.SessionStarted -> {
                            // Update session status locally
                            updateStatus(event.classId, ClassStatus.ONGOING)
                            // Reload sessions to get fresh data
                            loadTeacherSessions()
                        }
                        is WebSocketEvent.SessionEnded -> {
                            updateStatus(event.classId, ClassStatus.COMPLETED)
                            loadTeacherSessions()
                        }
                        is WebSocketEvent.StudentJoinedSession -> {
                            // Refresh session details to see new attendee
                            loadTeacherSessions()
                        }
                    }
                }
            }
        }
    }

    // Update startSession to use WebSocket
    fun startSession(classId: String, className: String) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                
                // Call backend API
                val response = apiService.startSession(token, classId)
                
                if (response.isSuccessful) {
                    // Update local status
                    updateStatus(classId, ClassStatus.ONGOING)
                    
                    // Send WebSocket event (triggers broadcast to students)
                    wsManager?.sendSessionStarted(classId, className, "")
                    
                    // Join the room for real-time updates
                    wsManager?.sendJoinRoom(classId)
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "Error starting session: ${e.message}")
            }
        }
    }

    // Update endSession to use WebSocket
    fun endSession(classId: String, sessionId: String) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                val response = apiService.endSession(token, sessionId)
                
                if (response.isSuccessful) {
                    updateStatus(classId, ClassStatus.COMPLETED)
                    
                    // Send WebSocket event
                    wsManager?.sendSessionEnded(classId, sessionId)
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "Error ending session: ${e.message}")
            }
        }
    }

    // Student: Join session
    fun joinSession(classId: String, sessionId: String) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                val response = apiService.attendSession(token, sessionId)
                
                if (response.isSuccessful) {
                    // Send WebSocket event to notify teacher
                    wsManager?.sendStudentJoined(classId, sessionId)
                    
                    // Join room for WebRTC
                    wsManager?.sendJoinRoom(classId)
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "Error joining session: ${e.message}")
            }
        }
    }
}
```

### Step 4: Update MainActivity to Initialize WebSocket

Update `MainActivity.kt`:

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var wsManager: WebSocketManager
    private lateinit var sessionViewModel: SessionViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize WebSocket Manager
        wsManager = WebSocketManager(
            tokenManager = TokenManager(this),
            baseUrl = AppConfig.BASE_URL
        )
        
        // Initialize ViewModel with WebSocket
        sessionViewModel = ViewModelProvider(
            this,
            SessionViewModelFactory(apiService, { tokenManager.getToken() }, wsManager)
        )[SessionViewModel::class.java]
        
        // Connect WebSocket when user is logged in
        if (tokenManager.isLoggedIn()) {
            wsManager.connect()
        }
        
        // ... rest of your existing code
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnect()
    }
}
```

### Step 5: Add Pull-to-Refresh

#### Teacher SessionsTab (`teacher/SessionsTab.kt`):

```kotlin
@Composable
fun SessionsTab(
    viewModel: SessionViewModel,
    onStartLive: (roomId: String, role: String) -> Unit,
    isTabSelected: Boolean = true
) {
    // Add this
    val refreshState = rememberSwipeRefreshState(isRefreshing = false)
    
    SwipeRefresh(
        state = rememberSwipeRefreshState(
            isRefreshing = false,
            onRefresh = {
                viewModel.loadTeacherSessions()
                viewModel.autoCompleteExpiredSessions()
            }
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        // Your existing LazyColumn or content
    }
}
```

#### Student StatusScreen (`Statusscreen.kt`):

```kotlin
@Composable
fun StatusScreen(
    navController: NavHostController,
    viewModel: SessionViewModel
) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(
            isRefreshing = false,
            onRefresh = {
                viewModel.loadStudentSessions()
            }
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        // Your existing content
    }
}
```

Add dependency in `app/build.gradle.kts`:

```kotlin
implementation("com.google.accompanist:accompanist-swiperefresh:0.34.0")
```

### Step 6: Reduce Polling Frequency (Optional)

Since WebSocket provides real-time updates, you can increase polling interval as fallback:

```kotlin
// In SessionViewModel.kt
@RequiresApi(Build.VERSION_CODES.O)
fun startStudentPolling() {
    viewModelScope.launch {
        while (true) {
            delay(60_000) // Changed from 10s to 60s (1 minute)
            loadStudentSessions()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun startTeacherPolling() {
    viewModelScope.launch {
        while (true) {
            delay(60_000) // Changed from 10s to 60s
            loadTeacherSessions()
        }
    }
}
```

## Testing the Implementation

### 1. Test Session Creation
```bash
# Create a class with schedule
POST /api/v1/classes
{
  "title": "Math Class",
  "description": "Algebra basics",
  "price": 500,
  "schedule": [
    {
      "startTime": "2026-04-09T17:50:00.000Z",
      "endTime": "2026-04-09T18:20:00.000Z"
    }
  ]
}
```

### 2. Test Session Start
```bash
# Teacher starts session
POST /api/v1/classes/{classId}/sessions/start
```

### 3. Test Session End
```bash
# Teacher ends session
POST /api/v1/sessions/{sessionId}/end
```

### 4. Test Student Attendance
```bash
# Student marks attendance
POST /api/v1/sessions/{sessionId}/attend
```

### 5. Verify WebSocket Events
- Open WebSocket connection
- Start session as teacher
- Verify student receives `session:started` event
- End session as teacher
- Verify student receives `session:ended` event

## Auto-Status Update Logic

### Backend Worker (Session Queue)

Create `src/workers/session.worker.ts`:

```typescript
import { Worker } from 'bullmq';
import { redisConnection } from '../config/redis';
import * as sessionService from '../services/session.service';
import { connectionManager } from '../ws/connectionManager';

const sessionWorker = new Worker(
  'sessionQueue',
  async (job) => {
    switch (job.name) {
      case 'CHECK_UPCOMING_SESSIONS':
        const upcoming = await sessionService.getUpcomingSessions(15);
        // Send reminder notifications
        break;
        
      case 'START_MISSED':
        const missed = await sessionService.markMissedSessions(15);
        console.log(`Marked ${missed.updatedCount} sessions as missed`);
        break;
        
      case 'AUTO_END':
        const ended = await sessionService.autoCompleteExpiredSessions();
        console.log(`Auto-completed ${ended.updatedCount} sessions`);
        break;
    }
  },
  { connection: redisConnection as any }
);

// Schedule recurring jobs
import { sessionQueue } from '../queues/session.queue';

// Run every 5 minutes
await sessionQueue.add(
  'AUTO_END',
  {},
  { repeat: { every: 5 * 60 * 1000 } } // 5 minutes
);

await sessionQueue.add(
  'START_MISSED',
  {},
  { repeat: { every: 5 * 60 * 1000 } }
);

export default sessionWorker;
```

Update `worker.ts`:

```typescript
import sessionWorker from './src/workers/session.worker';

// ... existing workers

// Session worker is already auto-started on import
```

## Summary

### What's Done ✅
- Backend session model with per-instance tracking
- Backend session service with start/end/attend logic
- Backend API endpoints for session management
- WebSocket events for real-time status updates
- Auto-creation of sessions from class schedule
- Android API service with session endpoints

### What Needs to Be Done on Android 🔧
1. Create WebSocketManager class
2. Update SessionViewModel to integrate WebSocket
3. Update MainActivity to initialize WebSocket
4. Add pull-to-refresh with Accompanist
5. Reduce polling frequency (optional)
6. Test end-to-end flow

### Expected Behavior After Implementation

**Teacher Side:**
1. Creates class with schedule (e.g., 5:50 PM - 6:20 PM)
2. Session auto-created from schedule
3. At 5:50 PM, teacher clicks "Start Session"
4. Status changes to "Ongoing" instantly (local + WebSocket broadcast)
5. All enrolled students see status change in real-time
6. Teacher clicks "End Session"
7. Status changes to "Completed" (local + WebSocket broadcast)

**Student Side:**
1. Sees "Upcoming" status before start time
2. At exact start time (when teacher starts), status changes to "Ongoing" automatically
3. "Join Live" button becomes active
4. If student joins, attendance is marked
5. After end time, status changes to "Completed"
6. If teacher never starts, status changes to "Missed" after 15 min grace period

**No Manual Refresh Required!** All updates happen via WebSocket in real-time.
