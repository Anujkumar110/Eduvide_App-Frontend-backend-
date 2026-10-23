# WebRTC Debug Quick Reference Card

## 📱 **Android LogCat Filters**

### Essential Logs to Watch:
```bash
# In Android Studio LogCat, filter by these tags:
WebRTCClient
SignalingClient  
CallViewModel
```

### Success Log Pattern (Teacher):
```
D/WebRTCClient: startAsTeacher()
D/WebRTCClient: ICE servers configured: 5
D/WebRTCClient:   - stun:stun.l.google.com:19302
D/WebRTCClient: PeerConnection built ✅
D/WebRTCClient: DataChannel created ✅
D/WebRTCClient: startLocalMedia() — starting teacher media
D/WebRTCClient:   Audio track added ✅
D/WebRTCClient:   Using camera: 0 (front)
D/WebRTCClient:   Local video sink attached ✅
D/WebRTCClient: startLocalMedia() — Teacher media setup complete ✅
D/SignalingClient: Connecting… roomId=CLASS_ID
D/SignalingClient: WS Connected
D/SignalingClient: → room:join | {classId: CLASS_ID, role: teacher}
D/SignalingClient: ← room:joined | {peers: []}
D/CallViewModel: Connected + joinRoom(role=teacher)
```

### Success Log Pattern (Student):
```
D/WebRTCClient: startAsViewer() — student will send video + audio to teacher
D/WebRTCClient: ICE servers configured: 5
D/WebRTCClient: PeerConnection built ✅
D/WebRTCClient: Student media tracks added to PeerConnection ✅
D/SignalingClient: Connecting… roomId=CLASS_ID
D/SignalingClient: WS Connected
D/SignalingClient: → room:join | {classId: CLASS_ID, role: student}
D/SignalingClient: ← room:joined | {peers: [teacherId]}
D/CallViewModel: Joined with 1 peers — sending offer
D/SignalingClient: ← webrtc:offer | {from: teacherId, sdp: ...}
D/WebRTCClient: handleOffer
D/WebRTCClient: Answer sent ✅
```

---

## 🚨 **Error Patterns & Solutions**

### ❌ Video Not Showing

**Check these logs:**
```
❌ "No camera found!" → Camera permission denied
❌ "remoteView is null!" → View not initialized
❌ "peerConnection is null!" → Connection not built
```

**Solution:**
1. Check permissions in Settings → Apps → EduVibe
2. Ensure `initCall()` receives valid SurfaceViewRenderer
3. Look for "PeerConnection built ✅" before video logs

---

### ❌ Audio Not Working

**Check these logs:**
```
❌ No "Audio track added ✅" → Audio setup failed
❌ "googEchoCancellation" not in logs → Constraints not applied
```

**Solution:**
1. Verify microphone permission granted
2. Check `startLocalMedia()` has audio constraints
3. Ensure `localAudioTrack?.setEnabled(true)` is called

---

### ❌ Chat Not Working

**Check these logs:**
```
❌ "Not connected — dropping: chat:message" → WebSocket disconnected
❌ "DataChannel state: CLOSED" → DataChannel not established
```

**Solution:**
1. Wait for "DataChannel created ✅" before sending
2. Check WebSocket connection is active
3. Verify both users in same room (same classId)

---

### ❌ Connection Failed

**Check these logs:**
```
❌ "Token empty — aborting" → Not logged in
❌ "WS Failure: ..." → Network/auth issue
❌ "room:error" → Not authorized for room
```

**Solution:**
1. Verify token in SharedPreferences is valid
2. Check backend logs for auth errors
3. Ensure room membership validation passes (currently set to `return true`)

---

## 🔧 **Quick Debug Commands**

### Check Backend is Running:
```bash
curl http://localhost:5000/api/v1/health
```

### Test ICE Config:
```bash
curl http://localhost:5000/api/v1/config/webrtc
```

### Monitor WebSocket Traffic (Chrome DevTools):
1. Open backend in browser
2. DevTools → Network → WS filter
3. Look for WebSocket frames

---

## 📊 **WebRTC State Checklist**

### Teacher Side:
- [ ] Camera permission ✅
- [ ] Microphone permission ✅
- [ ] `startAsTeacher()` called ✅
- [ ] "Local media started ✅" ✅
- [ ] "Connected + joinRoom" ✅
- [ ] "room:joined" received ✅
- [ ] Local video preview visible ✅
- [ ] Mic icon shows unmuted ✅

### Student Side:
- [ ] Camera permission ✅
- [ ] Microphone permission ✅
- [ ] `startAsViewer()` called ✅
- [ ] "Student media tracks added ✅" ✅
- [ ] "Connected + joinRoom" ✅
- [ ] "webrtc:offer" received ✅
- [ ] "Answer sent ✅" ✅
- [ ] Remote video visible ✅
- [ ] Can hear teacher ✅

### Both Sides:
- [ ] ICE connection successful ✅
- [ ] "ICE state: connected" ✅
- [ ] Chat messages sent/received ✅
- [ ] Toggle mic works ✅
- [ ] Toggle camera works ✅

---

## 🎯 **Common Scenarios**

### Scenario: Teacher sees black screen
**Problem:** Camera not starting
**Fix:** Check "No camera found" in logs, grant permission

### Scenario: Student can't see teacher
**Problem:** Offer/answer not exchanged
**Fix:** Verify both using same `classId`, check backend room logs

### Scenario: Can hear but can't see video
**Problem:** Video track not added
**Fix:** Ensure `addTrack()` called for video, check `onTrack` fires

### Scenario: Can see but can't hear
**Problem:** Audio track not added or muted
**Fix:** Check audio constraints, verify `setEnabled(true)`

### Scenario: Chat works one way
**Problem:** DataChannel not established
**Fix:** Wait for "DataChannel created ✅", use WebSocket fallback

---

## 💡 **Pro Tips**

1. **Always check BOTH sides** - teacher AND student logs
2. **Look for the ✅** - success logs have checkmarks
3. **Timestamp matters** - ensure events happen in order
4. **Room ID must match** - same `classId` on both sides
5. **Token must be valid** - expired tokens cause auth failures
6. **Clean rebuild after changes** - `./gradlew clean assembleDebug`

---

## 🆘 **When All Else Fails**

1. Restart backend: `cd backend-eduvibe-app-2.0 && npm run dev`
2. Clear app data on Android device
3. Login again to get fresh token
4. Check backend logs for errors
5. Share complete LogCat output for debugging

---

**Remember:** Your UI and logic are FINE - we're only fixing the WebRTC layer underneath!
