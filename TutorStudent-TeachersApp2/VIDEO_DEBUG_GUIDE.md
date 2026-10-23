# 🔍 Video Stream Debug Guide - EXTREME LOGGING

## 📋 **Ab Kya Karna Hai:**

### 1. Clean Build
```powershell
.\gradlew.bat clean assembleDebug installDebug
```

### 2. Check LogCat with These Filters:
```
WebRTCClient|CallScreen|CallViewModel
```

---

## 🎯 **Success Logs Pattern - What You SHOULD See:**

### **Teacher Side:**
```
D/CallScreen: 🎥 Creating remote SurfaceViewRenderer
D/CallScreen: 🎥 Creating teacher local SurfaceViewRenderer
D/CallScreen: ✅ Both renderers ready - initializing call
D/CallViewModel: 🎥 Starting as TEACHER
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: 🎥🎥🎥 startLocalMedia() - TEACHER
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: 🎤 Audio track created: true
D/WebRTCClient: 🎤 Audio track enabled: true
D/WebRTCClient:   ✅ Teacher audio track added to peerConnection
D/WebRTCClient:   📹 Using camera: 0 (front)
D/WebRTCClient:   📹 Camera capturer started: 1280x720@30fps
D/WebRTCClient:   📹 Video track created: true
D/WebRTCClient:   📹 Video track enabled: true
D/WebRTCClient:   📺 localView available - attaching video sink
D/WebRTCClient:   ✅ Teacher local preview started
D/WebRTCClient:   📹 Video track added to peerConnection
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: startLocalMedia() — ✅ Teacher media setup COMPLETE
D/WebRTCClient: ═══════════════════════════════════════════════════
D/CallViewModel: ✅✅✅ MEDIA READY - Connecting to signaling server
```

**When Student Joins:**
```
D/WebRTCClient: ════════════════════════════════════════════
D/WebRTCClient: 🎥🎥🎥 onTrack CALLED!
D/WebRTCClient: ════════════════════════════════════════════
D/WebRTCClient: 🎥 Track kind: video
D/WebRTCClient: 🎥 Track ID: VIDEO_TRACK_STUDENT
D/WebRTCClient: 🎥 Track enabled: true
D/WebRTCClient: 🎥 Track state: LIVE
D/WebRTCClient: ✅✅✅ Remote VIDEO track received!
D/WebRTCClient: 📺 remoteView is: READY ✅
D/WebRTCClient: 📺 Attaching video sink to remoteView...
D/WebRTCClient: ✅✅✅ VIDEO SINK ATTACHED - Video should be VISIBLE now!
D/WebRTCClient: ════════════════════════════════════════════
```

---

### **Student Side:**
```
D/CallScreen: 🎥 Creating remote SurfaceViewRenderer
D/CallScreen: 🎥 Creating student local SurfaceViewRenderer
D/CallScreen: ✅ Both renderers ready - initializing call
D/CallViewModel: 🎥 Starting as STUDENT
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: 🎥🎥🎥 startLocalMediaAsViewer() - STUDENT
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: 🎤 Audio track created: true
D/WebRTCClient: 🎤 Audio track enabled: true
D/WebRTCClient:   ✅ Student audio track added to peerConnection
D/WebRTCClient:   📹 Using camera: 0 (front)
D/WebRTCClient:   📹 Camera capturer started: 640x480@20fps
D/WebRTCClient:   📹 Video track created: true
D/WebRTCClient:   📹 Video track enabled: true
D/WebRTCClient:   📺 localView available - attaching video sink
D/WebRTCClient:   ✅ Student local preview started
D/WebRTCClient: ═══════════════════════════════════════════════════
D/WebRTCClient: startLocalMediaAsViewer() — ✅ Student media setup COMPLETE
D/WebRTCClient: ═══════════════════════════════════════════════════
```

**When Receiving Teacher's Video:**
```
D/WebRTCClient: ════════════════════════════════════════════
D/WebRTCClient: 🎥🎥🎥 onTrack CALLED!
D/WebRTCClient: ════════════════════════════════════════════
D/WebRTCClient: 🎥 Track kind: video
D/WebRTCClient: ✅✅✅ Remote VIDEO track received!
D/WebRTCClient: 📺 remoteView is: READY ✅
D/WebRTCClient: 📺 Attaching video sink to remoteView...
D/WebRTCClient: ✅✅✅ VIDEO SINK ATTACHED - Video should be VISIBLE now!
```

---

## 🚨 **Common Errors & What They Mean:**

### ❌ **"localView is NULL - preview won't show"**
**Problem:** SurfaceViewRenderer not created before media setup  
**Fix:** Check CallScreen - views should be created in AndroidView factory

### ❌ **"remoteView is NULL - cannot display video"**
**Problem:** Remote view not ready when video track arrives  
**Fix:** Wait for both renderers before calling initCall

### ❌ **"No camera found"**
**Problem:** Camera permission denied or no camera available  
**Fix:** Grant camera permission in app settings

### ❌ **"Track is NULL in onTrack"**
**Problem:** Peer connection didn't receive track  
**Fix:** Check if remote side added tracks to peer connection

### ❌ **No "onTrack CALLED!" message**
**Problem:** Remote side not sending tracks  
**Fix:** Verify remote side called addTrack() properly

---

## 📊 **What Each Log Means:**

| Log Message | Meaning |
|-------------|---------|
| `🎥 Creating remote SurfaceViewRenderer` | UI creating remote video view |
| `✅ Both renderers ready` | Both local and remote views ready |
| `🎤 Audio track created: true` | Audio track successfully created |
| `🎤 Audio track enabled: true` | Audio track is ENABLED (should be true) |
| `📹 Video track created: true` | Video track successfully created |
| `📹 Video track enabled: true` | Video track is ENABLED (should be true) |
| `📺 localView available` | Local preview can show |
| `✅ Teacher/Student media setup COMPLETE` | All tracks added to peer connection |
| `✅✅✅ MEDIA READY` | Safe to connect to signaling |
| `🎥🎥🎥 onTrack CALLED!` | **CRITICAL** - Remote video/audio arriving |
| `✅✅✅ VIDEO SINK ATTACHED` | **SUCCESS** - Video will display |

---

## 🎯 **Test Flow:**

1. **Start backend:** `npm run dev`
2. **Teacher login** → Start Live Class
3. **Watch LogCat** - should see all teacher logs above
4. **Student login** → Join same class  
5. **Watch LogCat** - should see all student logs above
6. **Check BOTH sides** for "onTrack CALLED!" and "VIDEO SINK ATTACHED"

---

## 💡 **If Still Not Working:**

Share these logs showing:
1. Complete teacher logs from start to "media setup COMPLETE"
2. Complete student logs from start to "media setup COMPLETE"  
3. Backend logs when student joins
4. Look for any "❌" error messages

**The logs will tell EXACTLY where video is failing!**
