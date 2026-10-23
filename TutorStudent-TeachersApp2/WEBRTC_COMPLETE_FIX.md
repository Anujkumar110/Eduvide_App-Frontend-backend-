# WebRTC Video Call, Audio & Chat - Complete Fix Guide

## 🔍 **Problems Identified**

### 1. **Video Streaming Issues**
- Race condition: Offer sent before camera track is ready
- Student `startLocalMediaAsViewer()` adds tracks but timing is off
- No proper wait for camera initialization before signaling

### 2. **Audio/Mic Not Working**
- Echo cancellation constraints might not be applied correctly
- Audio track enabled state not properly synced with UI
- No explicit audio track `setEnabled(true)` call in student mode

### 3. **Chat Not Working Reliably**
- Dual chat system (WebSocket + DataChannel) causes confusion
- DataChannel might not be properly established before messages sent
- No fallback mechanism if DataChannel fails

### 4. **Signaling Timing Issues**
- Teacher sends offer immediately without waiting for media setup
- No verification that tracks are ready before offer creation

---

## ✅ **Solutions - Android Side**

### **Fix 1: WebRTCClient.kt - Ensure Media Ready Before Signaling**

**File:** `app/src/main/java/com/example/tutorapp/Webrtcclient.kt`

#### **Problem:**
`startAsTeacher()` and `startAsViewer()` call `buildPeerConnection()` then `startLocalMedia()`, but there's no guarantee media tracks are ready before signaling starts.

#### **Solution:**
Add a callback mechanism to ensure media is ready before proceeding.

```kotlin
// Add this callback at class level
var onMediaReady: (() -> Unit)? = null

// In startLocalMedia(), after all tracks are added:
localVideoSender = pc.addTrack(localVideoTrack!!, listOf("LOCAL_STREAM"))
Log.d(TAG, "Local media started ✅")

// NEW: Notify that media is ready
mainHandler.post { onMediaReady?.invoke() }
```

#### **In startLocalMediaAsViewer(), add:**
```kotlin
// After pc.addTrack(localVideoTrack!!...)
localAudioTrack?.setEnabled(true)  // ✅ EXPLICIT enable
localVideoTrack?.setEnabled(true)  // ✅ EXPLICIT enable

Log.d(TAG, "Student media tracks added to PeerConnection ✅")
mainHandler.post { onMediaReady?.invoke() }  // ✅ Notify ready
```

---

### **Fix 2: CallViewModel.kt - Wait for Media Before Joining Room**

**File:** `app/src/main/java/com/example/tutorapp/viewmodel/Callviewmodel.kt`

#### **Problem:**
`client.connect(token)` is called immediately, but media might not be ready yet.

#### **Solution:**
```kotlin
// In initializeViewsAndStart(), AFTER creating rtc:

// Setup media ready callback
rtc.onMediaReady = {
    Log.d("CallViewModel", "Media ready, connecting to signaling")
    client.connect(token)
}

// DON'T connect immediately - remove this line:
// client.connect(token)  // ❌ REMOVE THIS

// Let the onMediaReady callback handle connection
if (isTeacher) rtc.startAsTeacher() else rtc.startAsViewer()
// client.connect(token)  // ❌ REMOVED - now called from onMediaReady
```

---

### **Fix 3: Enhanced Audio Setup**

**File:** `app/src/main/java/com/example/tutorapp/Webrtcclient.kt`

#### **In startLocalMedia() and startLocalMediaAsViewer():**

Add these audio constraints for better echo cancellation:

```kotlin
val audioConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))  // ✅ NEW
    mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))  // ✅ NEW
}
```

And explicitly enable audio tracks:

```kotlin
localAudioTrack = factory.createAudioTrack("AUDIO_TRACK", audioSource)
localAudioTrack?.setEnabled(true)  // ✅ EXPLICIT enable
pc.addTrack(localAudioTrack!!, listOf("LOCAL_STREAM"))
```

---

### **Fix 4: Chat Reliability - Dual Channel Fallback**

**File:** `app/src/main/java/com/example/tutorapp/viewmodel/Callviewmodel.kt`

#### **Problem:**
Chat messages sometimes lost if sent via DataChannel before it's ready.

#### **Solution:**
Always send via BOTH channels, but deduplicate on receive:

```kotlin
fun sendChat(sender: String, text: String) {
    if (text.isBlank()) return

    // Add to local chat list immediately
    _chatMessages.value = _chatMessages.value + Triple(sender, text, true)

    // 1. Send via WebSocket (primary)
    signalingClient?.sendChat(sender, text)
    Log.d("CallViewModel", "📤 Chat sent via WebSocket: $sender: $text")

    // 2. ALSO send via DataChannel (backup)
    val message = "DCHAT:$sender:$text"  // ✅ Prefix to identify
    webRTCClient?.sendMessage(message)
    Log.d("CallViewModel", "📤 Chat sent via DataChannel: $sender: $text")
}
```

**In onMessageReceived callback (WebRTCClient):**
```kotlin
rtc.onMessageReceived = { message ->
    try {
        if (message.startsWith("DCHAT:")) {
            // This is a duplicate check message
            val parts = message.removePrefix("DCHAT:").split(":", limit = 2)
            if (parts.size == 2) {
                val sender = parts[0]
                val text = parts[1]
                val isFromMe = (sender == currentUserName)
                
                // Check if already received via WebSocket
                val alreadyExists = _chatMessages.value.any { 
                    it.first == sender && it.second == text && it.third == isFromMe 
                }
                
                if (!alreadyExists) {
                    _chatMessages.value = _chatMessages.value + Triple(sender, text, isFromMe)
                    Log.d("CallViewModel", "💬 Chat received via DataChannel (was missing): $sender: $text")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("CallViewModel", "DataChannel message parse error: ${e.message}")
    }
}
```

**In onChatMessage callback (SignalingClient):**
```kotlin
client.onChatMessage = { sender, text ->
    val isFromMe = (sender == currentUserName)
    val alreadyExists = _chatMessages.value.any { 
        it.first == sender && it.second == text 
    }
    
    if (!alreadyExists) {
        _chatMessages.value = _chatMessages.value + Triple(sender, text, isFromMe)
        Log.d("CallViewModel", "💬 Chat received via WebSocket: $sender: $text")
    }
}
```

---

### **Fix 5: Better Logging for Debugging**

**File:** `app/src/main/java/com/example/tutorapp/Webrtcclient.kt`

Add detailed logging at critical points:

```kotlin
// In onTrack callback:
override fun onTrack(transceiver: RtpTransceiver) {
    val track = transceiver.receiver.track() ?: run {
        Log.w(TAG, "onTrack — track is null!")
        return
    }
    Log.d(TAG, "🎥 onTrack — kind: ${track.kind()}, id: ${track.id()}")
    
    if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
        val remoteVideoTrack = track as VideoTrack
        mainHandler.post {
            remoteView?.let {
                remoteVideoTrack.addSink(it)
                Log.d(TAG, "✅ Remote video sink attached - video should be visible now!")
            } ?: Log.e(TAG, "❌ remoteView is null - cannot display video!")
        }
    } else if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
        Log.d(TAG, "✅ Remote audio track received - audio should work!")
    }
}
```

---

## ✅ **Solutions - Backend Side**

### **Fix 6: Verify Signaling Handler Routes Messages Correctly**

**File:** `backend-eduvibe-app-2.0/src/ws/handlers/signalingHandler.ts`

The current implementation looks correct, but add logging:

```typescript
if (type === "webrtc:room-offer") {
    const { classId, sdp } = payload as { classId: string; sdp: unknown };
    const peer = roomManager.getPeer(classId, senderId);
    
    if (peer) {
        console.log(`[Signaling] Forwarding offer from ${senderId} to ${peer} for room ${classId}`);
        connectionManager.send(peer, {
            type: "webrtc:offer",
            payload: { from: senderId, sdp },
        });
    } else {
        console.warn(`[Signaling] No peer found in room ${classId} for offer from ${senderId}`);
    }
    return;
}
```

---

## 🧪 **Testing Checklist**

### **Before Testing:**
1. ✅ Clean build: `./gradlew clean assembleDebug`
2. ✅ Restart backend server
3. ✅ Check logs for both Android and backend

### **Test Flow:**

#### **1. Teacher Side:**
```
1. Teacher clicks "Start Live Class"
2. Check logs:
   - "startAsTeacher()" ✅
   - "ICE servers configured" ✅
   - "Local media started ✅" ✅
   - "Media ready" callback fires ✅
3. Check video preview shows teacher's camera ✅
4. Check microphone icon shows unmuted ✅
```

#### **2. Student Side:**
```
1. Student clicks "Join Class"
2. Check logs:
   - "startAsViewer()" ✅
   - "Student media tracks added ✅" ✅
   - "Media ready" callback fires ✅
3. Check video shows teacher's stream ✅
4. Check student's small preview shows their camera ✅
5. Check audio works (student can hear teacher) ✅
```

#### **3. Chat Test:**
```
1. Teacher sends message
2. Check logs: "Chat sent via WebSocket" AND "Chat sent via DataChannel" ✅
3. Student receives message (check both see it) ✅
4. Student replies
5. Teacher receives reply ✅
6. No duplicate messages ✅
```

#### **4. Audio/Mic Test:**
```
1. Teacher speaks - student should hear ✅
2. Student speaks - teacher should hear ✅
3. Toggle mic buttons work ✅
4. Echo cancellation working (no feedback loop) ✅
```

---

## 🐛 **Common Issues & Quick Fixes**

### **Issue: Black Screen (No Video)**
**Check:**
- Camera permission granted? → Check logs for "No camera found"
- `localView` initialized? → Check for "remoteView is null" errors
- Offer/answer exchanged? → Check WebSocket logs for `webrtc:offer` and `webrtc:answer`

**Fix:** Ensure `onMediaReady` callback fires before `connect(token)`

---

### **Issue: No Audio**
**Check:**
- Microphone permission granted?
- Audio track enabled? → Add `localAudioTrack?.setEnabled(true)` explicitly
- Echo cancellation working? → Check audio constraints

**Fix:** Add all audio constraints listed in Fix 3

---

### **Issue: Chat Not Working**
**Check:**
- WebSocket connected? → Check `isConnected` flag
- DataChannel state? → Should be "OPEN"
- Messages deduplicated? → Check `alreadyExists` logic

**Fix:** Use dual-channel approach (Fix 4)

---

### **Issue: Connection Failed**
**Check:**
- Token valid? → Check WebSocket auth logs
- Room joined? → Check for `room:joined` message
- ICE servers configured? → Check ICE server logs

**Fix:** Ensure `fetchIceServers()` completes before WebRTC init

---

## 📋 **Summary of Changes**

| File | Change | Impact |
|------|--------|--------|
| `Webrtcclient.kt` | Add `onMediaReady` callback | Ensures media ready before signaling |
| `Webrtcclient.kt` | Explicit `setEnabled(true)` on tracks | Fixes audio/video not starting |
| `Webrtcclient.kt` | Enhanced audio constraints | Better echo cancellation |
| `Callviewmodel.kt` | Wait for media before connect | Prevents race conditions |
| `Callviewmodel.kt` | Dual-channel chat with dedup | Reliable chat delivery |
| `signalingHandler.ts` | Add logging | Better debugging |

---

## 🚀 **Next Steps**

1. Apply the fixes from this document
2. Clean rebuild the Android app
3. Test the complete flow
4. Check logs for any errors
5. Report specific error messages if issues persist

**Your existing UI and logic will NOT be broken** - these fixes only improve the WebRTC layer underneath!
